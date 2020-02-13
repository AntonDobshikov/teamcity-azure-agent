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

package jetbrains.buildServer.clouds.azure.arm

import jetbrains.buildServer.clouds.azure.AzureProperties
import jetbrains.buildServer.clouds.base.AbstractCloudInstance
import jetbrains.buildServer.serverSide.AgentDescription

/**
 * @author Sergey.Pak
 * *         Date: 7/31/2014
 * *         Time: 7:15 PM
 */
class AzureCloudInstance internal constructor(image: AzureCloudImage, name: String)
    : AbstractCloudInstance<AzureCloudImage>(image, name, name) {

    var properties: MutableMap<String, String> = HashMap()

    override fun containsAgent(agent: AgentDescription): Boolean {
        val agentInstanceName = agent.configurationParameters[AzureProperties.INSTANCE_NAME]
        return name.equals(agentInstanceName, ignoreCase = true)
    }
}