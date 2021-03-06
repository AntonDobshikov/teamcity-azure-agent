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

package jetbrains.buildServer.clouds.utils

import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import org.testng.Assert
import org.testng.annotations.Test

@Test
class ArmTemplateBuilderTest {

    fun testSetTemplateTags() {
        val builder = ArmTemplateBuilder("""{
        "resources": [
            {"name": "[parameters('vmName')]"},
            {"vm": "vm"}
        ]}""").setTags("[parameters('vmName')]", mapOf(AzureConstants.TAG_PROFILE to "profile"))

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"name":"[parameters('vmName')]","tags":{"teamcity-profile":"profile"}},{"vm":"vm"}]}""")
    }

    fun testSetPublicIp() {
        val builder = ArmTemplateBuilder("""{"variables": {}, "resources": [{
      "name": "[variables('nicName')]",
      "properties": {
        "ipConfigurations": [
          {
            "properties": { }
          }
        ]
      }
    }]}""").setPublicIp()

        Assert.assertEquals(builder.toString(),
                """{"variables":{"pipName":"[concat(parameters('vmName'), '-pip')]"},"resources":[{"name":""" +
                        """"[variables('nicName')]","properties":{"ipConfigurations":[{"properties":{"publicIPAddress"""" +
                        """:{"id":"[resourceId('Microsoft.Network/publicIPAddresses', variables('pipName'))]"}}}]},""" +
                        """"dependsOn":["[concat('Microsoft.Network/publicIPAddresses/', variables('pipName'))]"]},""" +
                        """{"apiVersion":"2016-09-01","type":"Microsoft.Network/publicIPAddresses","name":""" +
                        """"[variables('pipName')]","location":"[variables('location')]","properties":{""" +
                        """"publicIPAllocationMethod":"Dynamic"}}]}""")
    }

    fun testAddParameter() {
        val builder = ArmTemplateBuilder("""{"parameters": {}}""").addParameter("name", "string", "description")

        Assert.assertEquals(builder.toString(),
                """{"parameters":{"name":{"type":"string","metadata":{"description":"description"}}}}""")
    }

    fun testSetCustomImage() {
        val builder = ArmTemplateBuilder("""{"resources": [{
      "name": "[parameters('vmName')]",
      "properties": {
        "storageProfile": {}
      }
    }]}""").setCustomImage()

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"name":"[parameters('vmName')]","properties":{"storageProfile":{""" +
                        """"imageReference":{"id":"[parameters('imageId')]"}}}}]}""")
    }

    fun testSetVhdImage() {
        val builder = ArmTemplateBuilder("""{"resources": [{
      "name": "[parameters('vmName')]",
      "properties": {
        "storageProfile": {
          "osDisk": {}
        }
      }
    }]}""").setVhdImage()

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"name":"[parameters('vmName')]","properties":{"storageProfile":{"osDisk":{""" +
                        """"image":{"uri":"[parameters('imageUrl')]"},"vhd":{"uri":"[concat('https://', """ +
                        """split(parameters('imageUrl'),'/')[2], '/vhds/', parameters('vmName'), '-os.vhd')]"}}}}}]}""")
    }

    fun testAddContainer() {
        val builder = ArmTemplateBuilder("""{"resources": [
      {
        "type": "Microsoft.ContainerInstance/containerGroups"
      }
    ]}""").addContainer("aci-1", listOf(Pair("VAR1", "VALUE1")))

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"type":"Microsoft.ContainerInstance/containerGroups","properties":{"containers":[{"name":"aci-1","properties":{"image":"[parameters('imageId')]","environmentVariables":[{"name":"SERVER_URL","value":"[parameters('teamcityUrl')]"},{"name":"AGENT_NAME","value":"aci-1"},{"name":"VAR1","value":"VALUE1"}],"resources":{"requests":{"cpu":"[parameters('numberCores')]","memoryInGb":"[parameters('memory')]"}}}}]}}]}""")
    }

    fun testAddContainerVolumes() {
        val builder = ArmTemplateBuilder("""{"resources": [
      {
        "type": "Microsoft.ContainerInstance/containerGroups",
        "name": "myName",
        "properties": {
          "containers": [
            {
              "name": "myName"
            }
          ]
        }
      }
    ]}""").addContainerVolumes("myName", "aci-1")

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"type":"Microsoft.ContainerInstance/containerGroups","name":"myName","properties":{"containers":[{"name":"myName","properties":{"volumeMounts":[{"name":"aci-1","mountPath":"/var/lib/waagent/","readOnly":true},{"name":"aci-1-plugins","mountPath":"/opt/buildagent/plugins/"},{"name":"aci-1-logs","mountPath":"/opt/buildagent/logs/"},{"name":"aci-1-system","mountPath":"/opt/buildagent/system/.teamcity-agent/"},{"name":"aci-1-tools","mountPath":"/opt/buildagent/tools/"}]}}],"volumes":[{"name":"aci-1","azureFile":{"shareName":"aci-1","storageAccountName":"[parameters('storageAccountName')]","storageAccountKey":"[parameters('storageAccountKey')]"}},{"name":"aci-1-logs","azureFile":{"shareName":"aci-1-logs","storageAccountName":"[parameters('storageAccountName')]","storageAccountKey":"[parameters('storageAccountKey')]"}},{"name":"aci-1-plugins","azureFile":{"shareName":"aci-1-plugins","storageAccountName":"[parameters('storageAccountName')]","storageAccountKey":"[parameters('storageAccountKey')]"}},{"name":"aci-1-system","azureFile":{"shareName":"aci-1-system","storageAccountName":"[parameters('storageAccountName')]","storageAccountKey":"[parameters('storageAccountKey')]"}},{"name":"aci-1-tools","azureFile":{"shareName":"aci-1-tools","storageAccountName":"[parameters('storageAccountName')]","storageAccountKey":"[parameters('storageAccountKey')]"}}]}}],"parameters":{"storageAccountName":{"type":"String","metadata":{"description":""}},"storageAccountKey":{"type":"SecureString","metadata":{"description":""}}}}""")
    }

    fun testAddContainerEnvironment() {
        val builder = ArmTemplateBuilder("""{"resources": [
      {
        "type": "Microsoft.ContainerInstance/containerGroups",
        "name": "myName",
        "properties": {
          "containers": [
            {
              "name": "myName"
            }
          ]
        }
      }
    ]}""").addContainerEnvironment("myName", mapOf("key" to "value"))

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"type":"Microsoft.ContainerInstance/containerGroups","name":"myName","properties":{"containers":[{"name":"myName","properties":{"environmentVariables":[{"name":"key","value":"value"}]}}]}}]}""")
    }
}
