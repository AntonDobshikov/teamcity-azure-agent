/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.TeamCityProperties
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

class AzureThrottlerInterceptor(
        private val remainingReadsNotifier: AzureThrottlerAdapterRemainingReadsNotifier,
        private val name: String
) : Interceptor {
    private val myThrottlerDelayInMilliseconds = AtomicLong(0)
    private val myRequestsSequenceLength = ThreadLocal<Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val sleepTime = myThrottlerDelayInMilliseconds.get()
        if (sleepTime != 0L) {
            Thread.sleep(sleepTime)
        }

        val request = chain.request()
        val response = chain.proceed(request)

        val remainingReads = getHeaderLongValue(response, SUBSCRIPTION_REMAINING_READS_HEADER);
        val remainingResourceReads = getHeaderLongValue(response, SUBSCRIPTION_RESOURCE_REMAINING_READS_HEADER);
        val remainingTenantReads = getHeaderLongValue(response, TENANT_REMAINING_READS_HEADER);
        val remainingTenantResourceReads = getHeaderLongValue(response, TENANT_RESOURCE_REMAINING_READS_HEADER);

        val remainingReadsResult = remainingReads ?: remainingTenantReads ?: remainingResourceReads ?: remainingTenantResourceReads;

        LOG.debug("[$name] Azure request processed: Remaining reads: $remainingReadsResult, Url: ${request.url()}")
        LOG.debug("[$name] Azure request processed: Requests sequence length: ${myRequestsSequenceLength.get()}")
        LOG.debug("[$name] Azure request processed: Headers: $SUBSCRIPTION_REMAINING_READS_HEADER=$remainingReads, " +
                "$SUBSCRIPTION_RESOURCE_REMAINING_READS_HEADER=$remainingResourceReads, " +
                "$TENANT_REMAINING_READS_HEADER=$remainingTenantReads, " +
                "$TENANT_RESOURCE_REMAINING_READS_HEADER=$remainingTenantResourceReads, ")

        increaseRequestsSequenceLength()

        remainingReadsNotifier.notifyRemainingReads(remainingReadsResult)

        if (response.code() == RETRY_AFTER_STATUS_CODE) {
            val retryAfterSeconds = getRetryAfterSeconds(response)
            LOG.info("[$name] Azure Resource Manager read/write per hour limit reached. Will retry in: $retryAfterSeconds seconds")
            throw ThrottlerRateLimitReachedException(retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS)
        }
        return response
    }

    fun setThrottlerTime(milliseconds: Long) {
        val originalValue = myThrottlerDelayInMilliseconds.get()
        if (originalValue != milliseconds) {
            LOG.debug("[$name] Throttling delay changed from $originalValue ms to $milliseconds ms")
        }
        myThrottlerDelayInMilliseconds.set(milliseconds)
    }

    fun getThrottlerTime(): Long {
        return myThrottlerDelayInMilliseconds.get()
    }

    fun onBeginRequestsSequence() {
        myRequestsSequenceLength.set(0)
    }

    fun onEndRequestsSequence() {
        myRequestsSequenceLength.remove()
    }

    fun getRequestsSequenceLength() : Long? {
        return myRequestsSequenceLength.get()
    }

    private fun increaseRequestsSequenceLength() {
        myRequestsSequenceLength.get()?.let {
            myRequestsSequenceLength.set(it + 1)
        }
    }

    private fun getRetryAfterSeconds(response: Response): Long? {
        try {
            val retryAfterHeader = response.header(RETRY_AFTER)
            var retryAfter = 0L
            if (retryAfterHeader != null) {
                retryAfter = Integer.parseInt(retryAfterHeader).toLong()
            }
            if (retryAfter <= 0) {
                val bodyContent = content(response.body()) ?: ""
                var pattern = Pattern.compile(TRY_AGAIN_AFTER_MINUTES_PATTERN, Pattern.CASE_INSENSITIVE)
                var matcher = pattern.matcher(bodyContent)
                if (matcher.find()) {
                    retryAfter = TimeUnit.MINUTES.toSeconds(Integer.parseInt(matcher.group(1)).toLong())
                } else {
                    pattern = Pattern.compile(TRY_AGAIN_AFTER_SECONDS_PATTERN, Pattern.CASE_INSENSITIVE)
                    matcher = pattern.matcher(bodyContent)
                    if (matcher.find()) {
                        retryAfter = Integer.parseInt(matcher.group(1)).toLong()
                    }
                }
            }

            if (retryAfter > 0)
                return retryAfter
        }
        catch(e: Throwable) {
            LOG.warnAndDebugDetails("[$name] Exception occurred during read Retry After timeout value", e)
        }
        return null
    }

    private fun content(responseBody: ResponseBody?): String? {
        if (responseBody == null) {
            return null
        }
        val source = responseBody.source()
        source.request(java.lang.Long.MAX_VALUE)
        val buffer = source.buffer()
        return buffer.readUtf8()
    }

    private fun getHeaderLongValue(response: Response, name: String) : Long? {
        val value = response.header(name)
        return if (value.isNullOrEmpty()) null else Integer.parseInt(value).toLong();
    }

    companion object {
        private val LOG = Logger.getInstance(AzureThrottlerInterceptor::class.java.name)
        private const val SUBSCRIPTION_REMAINING_READS_HEADER = "x-ms-ratelimit-remaining-subscription-reads"
        private const val SUBSCRIPTION_RESOURCE_REMAINING_READS_HEADER = "x-ms-ratelimit-remaining-subscription-resource-requests"
        private const val TENANT_REMAINING_READS_HEADER = "x-ms-ratelimit-remaining-tenant-reads"
        private const val TENANT_RESOURCE_REMAINING_READS_HEADER = "x-ms-ratelimit-remaining-tenant-resource-requests"

        private const val RETRY_AFTER = "Retry-After"
        private const val TRY_AGAIN_AFTER_MINUTES_PATTERN = "try again after '([0-9]*)' minutes"
        private const val TRY_AGAIN_AFTER_SECONDS_PATTERN = "try again after '([0-9]*)' seconds"
        private const val DEFAULT_RETRY_AFTER_SECONDS = 5 * 60L
        private const val RETRY_AFTER_STATUS_CODE = 429
    }
}
