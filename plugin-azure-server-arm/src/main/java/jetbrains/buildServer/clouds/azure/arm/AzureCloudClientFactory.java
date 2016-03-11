/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.azure.AzurePropertiesNames;
import jetbrains.buildServer.clouds.azure.AzureUtils;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.server.impl.CloudManagerBase;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Constructs Azure ARM cloud clients.
 */
public class AzureCloudClientFactory extends AbstractCloudClientFactory<AzureCloudImageDetails, AzureCloudClient> {

    private final File myAzureStorage;
    private final PluginDescriptor myPluginDescriptor;
    private static final List<String> SKIP_PARAMETERS = Arrays.asList(AzureConstants.GROUP_ID, AzureConstants.STORAGE_ID,
            AzureConstants.IMAGE_PATH, AzureConstants.MAX_INSTANCES_COUNT, AzureConstants.VM_NAME_PREFIX,
            AzureConstants.VM_USERNAME, AzureConstants.VM_PASSWORD, AzureConstants.OS_TYPE);

    public AzureCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                   @NotNull final EventDispatcher<BuildServerListener> serverDispatcher,
                                   @NotNull final CloudManagerBase cloudManager,
                                   @NotNull final PluginDescriptor pluginDescriptor,
                                   @NotNull final ServerPaths serverPaths) {
        super(cloudRegistrar);
        myAzureStorage = new File(serverPaths.getPluginDataDirectory(), "cloud-" + getCloudCode() + "/indices");
        if (!myAzureStorage.exists()) {
            //noinspection ResultOfMethodCallIgnored
            myAzureStorage.mkdirs();
        }

        myPluginDescriptor = pluginDescriptor;

        serverDispatcher.addListener(new BuildServerAdapter() {
            @Override
            public void agentStatusChanged(@NotNull final SBuildAgent agent, final boolean wasEnabled, final boolean wasAuthorized) {
                if (!agent.isAuthorized() || wasAuthorized) {
                    return;
                }

                final Map<String, String> config = agent.getConfigurationParameters();
                if (config.containsKey(AzurePropertiesNames.INSTANCE_NAME) && !config.containsKey(CloudContants.PROFILE_ID)) {
                    // windows azure agent connected
                    for (CloudProfile profile : cloudManager.listProfiles()) {
                        final CloudClientEx existingClient = cloudManager.getClientIfExists(profile.getProfileId());
                        if (existingClient == null)
                            continue;
                        final CloudInstance instanceByAgent = existingClient.findInstanceByAgent(agent);
                        if (instanceByAgent != null) {
                            // we found instance and profile. Now updating parameters
                            return;
                        }
                    }
                }
            }
        });
    }

    @Override
    public AzureCloudClient createNewClient(@NotNull final CloudState state,
                                            @NotNull final Collection<AzureCloudImageDetails> images,
                                            @NotNull final CloudClientParameters params) {

        return createNewClient(params, images, Collections.<TypedCloudErrorInfo>emptyList());
    }

    @Override
    public AzureCloudClient createNewClient(@NotNull final CloudState state,
                                            @NotNull final CloudClientParameters params,
                                            @NotNull final TypedCloudErrorInfo[] errors) {
        return createNewClient(params, Collections.<AzureCloudImageDetails>emptyList(), Arrays.asList(errors));
    }

    @NotNull
    private AzureCloudClient createNewClient(final CloudClientParameters params,
                                             final Collection<AzureCloudImageDetails> images,
                                             final List<TypedCloudErrorInfo> errors) {
        final String tenantId = getParameter(params, AzureConstants.TENANT_ID);
        final String clientId = getParameter(params, AzureConstants.CLIENT_ID);
        final String clientSecret = getParameter(params, AzureConstants.CLIENT_SECRET);
        final String subscriptionId = getParameter(params, AzureConstants.SUBSCRIPTION_ID);

        final CloudApiConnector apiConnector = new AzureApiConnector(tenantId, clientId, clientSecret, subscriptionId);
        final AzureCloudClient azureCloudClient = new AzureCloudClient(params, images, apiConnector, myAzureStorage);
        azureCloudClient.updateErrors(errors);

        return azureCloudClient;
    }

    @NotNull
    private String getParameter(final CloudClientParameters params, final String parameter) {
        final String subscriptionId = params.getParameter(parameter);
        if (StringUtil.isEmpty(subscriptionId)) {
            throw new RuntimeException(parameter + " must not be empty");
        }
        return subscriptionId;
    }

    @Override
    public Collection<AzureCloudImageDetails> parseImageData(final CloudClientParameters params) {
        return AzureUtils.parseImageData(AzureCloudImageDetails.class, params);
    }

    @Nullable
    @Override
    protected TypedCloudErrorInfo[] checkClientParams(@NotNull final CloudClientParameters params) {
        return new TypedCloudErrorInfo[0];
    }

    @NotNull
    public String getCloudCode() {
        return "arm";
    }

    @NotNull
    public String getDisplayName() {
        return "Azure Resource Manager";
    }

    @Nullable
    public String getEditProfileUrl() {
        return myPluginDescriptor.getPluginResourcesPath("settings.html");
    }

    @NotNull
    public Map<String, String> getInitialParameterValues() {
        return Collections.emptyMap();
    }

    @NotNull
    public PropertiesProcessor getPropertiesProcessor() {
        return new PropertiesProcessor() {
            public Collection<InvalidProperty> process(Map<String, String> properties) {
                final List<String> keys = new ArrayList<String>(properties.keySet());
                for (String key : keys) {
                    if (SKIP_PARAMETERS.contains(key)) {
                        properties.remove(key);
                    }
                }

                return Collections.emptyList();
            }
        };
    }

    public boolean canBeAgentOfType(@NotNull final AgentDescription description) {
        return description.getConfigurationParameters().containsKey(AzurePropertiesNames.INSTANCE_NAME);
    }
}
