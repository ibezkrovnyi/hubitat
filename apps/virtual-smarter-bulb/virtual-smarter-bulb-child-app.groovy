/**
 *  ALL MY LATEST HUBITAT APPS AND DRIVERS: https://github.com/ibezkrovnyi/hubitat
 *
 *  ==== Smarter Bulb (Child App) ========================
 *  Group bulbs and control dimming level, color and color temperature without triggering bulbs 'on'
 *  > I use it to automatically change color temperature of almost all house bulbs according to the time of day.
 *
 *  Copyright 2021 Igor Bezkrovnyi (@ibezkrovnyi)
 *
 *  ==== LICENSE =========================================
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  ==== MODIFICATIONS ===================================
 *
 *    ... PLEASE PUT HERE YOUR CREDITS AND CHANGE LIST IF YOU MODIFY THIS FILE ...
 *
 *  ==== CHANGES ===================================
 *  1.0.2 - 2020-01-03 - Add more logs, add version, cleanup
 *  1.0.1 - 2020-01-02 - Initial release
 */
import groovy.transform.Field

@Field static String VERSION = '1.0.2'

// @Field static String DRIVER_NAME = 'Virtual - Smarter Bulb'
// @Field static String DRIVER_NAMESPACE = 'ibezkrovnyi'
@Field static String PROJECT_NAME = 'Smarter Bulb'
@Field static String PROJECT_NAMESPACE = 'ibezkrovnyi'
@Field static String DRIVER_NAME = 'Virtual RGBW Light'
@Field static String DRIVER_NAMESPACE = 'hubitat'
@Field static String CATEGORY = 'My Apps'

@Field static ArrayList<String> SUPPORTED_ATTRIBUTES = ['level', 'colorTemperature', 'hue', 'saturation']

private getATTRIBUTE_TO_COMMAND() {
  [
    'level': [
      command: 'setLevel',
      eventValueToAttributeValue: { toInt it },
    ],
    'hue': [
      command: 'setHue',
      eventValueToAttributeValue: { toInt it },
    ],
    'saturation': [
      command: 'setSaturation',
      eventValueToAttributeValue: { toInt it },
    ],
    'colorTemperature': [
      command: 'setColorTemperature',
      eventValueToAttributeValue: { toInt it },
    ],
  ]
}

definition(
  name: "Virtual - ${PROJECT_NAME} (Child)",
  namespace: PROJECT_NAMESPACE,
  author: 'Igor Bezkrovnyi',
  description: "Group Bulbs to change their dimming level, color temperature and color without triggering bulb On",
  category: CATEGORY,
  parent: "${PROJECT_NAMESPACE}:Virtual - ${PROJECT_NAME} (Parent)",
  filename: 'virtual-smarter-bulb-child-app.groovy',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  importUrl: 'https://raw.githubusercontent.com/ibezkrovnyi/hubitat/main/apps/virtual-smarter-bulb/virtual-smarter-bulb-child-app.groovy',
)

preferences {
  page name: 'configurationPage', title: app.label, install: true, uninstall: true
}

def configurationPage() {
  dynamicPage(name: 'configurationPage') {
    section("Application: <b>${PROJECT_NAME}@${VERSION}</b>") {
    }
    section('Control these Bulbs:') {
      input 'slaves', 'capability.switch', multiple: true, title: 'Select Bulbs to Control...', required: true, submitOnChange: true
    }
    section("By using this <b>${DRIVER_NAME}</b> Master device:") {
      input "useExistingMasterDevice", "bool", title: "Create a new master device with driver ${DRIVER_NAME} <b>(off)</b> or use existing one <b>(on)</b>", defaultValue: false, submitOnChange: true
      if (slaves) {
        if (!useExistingMasterDevice) {
          def defaultMasterDeviceName = "${PROJECT_NAME} - ${slaves?.label?.join(' and ')}"
          if (!newMasterDeviceName) app.updateSetting('newMasterDeviceName', defaultMasterDeviceName)
          input "newMasterDeviceName", "text", title: "<b>DEVICE NAME</b> (optional)", defaultValue: defaultMasterDeviceName, required: false, submitOnChange: true
          createMasterDevice(newMasterDeviceName ?: defaultMasterDeviceName)
          paragraph "Creation status=${atomicState.masterDeviceCreationStatus}"
        } else {
          deleteMasterDevice()
          app.removeSetting("newMasterDeviceName")

          input 'existingMaster', 'capability.bulb', multiple: false, title: 'Select Master Virtual Bulb...', required: true, submitOnChange: true
          paragraph "<small>* Device must use the <b>${DRIVER_NAME}</b> driver</small>"
        }
      }
    }

    def showAppLabelSection = useExistingMasterDevice ? !!existingMaster : atomicState.masterDeviceNetworkId != null
    if (showAppLabelSection) {
      section('<b>CHILD APP NAME</b> (optional)') {
        def master = getMasterDevice()
        if (master) {
          label title: '', description: master.displayName, required: false
          if (!app.label) app.updateLabel(master.displayName)
        }
      }
    } else {
      section('No MASTER DEVICE YET') {}
    }
  }
}

void installed() {
  installHandlers()
}

void updated() {
  installHandlers()
}

void uninstalled() {
  getChildDevices().each {
    deleteChildDevice(it.deviceNetworkId)
  }
}

private installHandlers(ignoredMasterDevice = null) {
  unsubscribe()
  unschedule()
  atomicState.pending = []
  atomicState.remove('queue')

  def master = getMasterDevice()
  subscribe(master, masterHandler, [filterEvents: false])
  subscribe(slaves, slavesHandler, [filterEvents: false])
}

@Field static Object slavesHandlerMutex = new Object()
void slavesHandler(event) {
  synchronized (slavesHandlerMutex) {
    // logInfo "slavesHandler name=${event.name} value=${event.value}"
    def slave = event.getDevice()
    if (event.name == 'switch' && event.value == 'on') {
      updateDevice(slave)
    }
  }
}

private updateDevice(slave) {
  def data = atomicState.pending.find { it.dni == slave.deviceNetworkId }
  if (!data) {
    logInfo "updateDevice - no pending changes for dni='${slave.deviceNetworkId}'"
    return
  }
  atomicState.pending -= data

  def slaveDeviceChangedDirectlySincePendingUpdate = data.attributes.any { it.value != slave.currentValue(it.key) }
  if (slaveDeviceHadNoChangesSincePendingUpdate) {
    logInfo "updateDevice - attributes were changed since pending update and they take precedence. Skipping device with dni=${slave.deviceNetworkId} update now"
    logDebug "updateDevice - device with dni=${slave.deviceNetworkId} details: prev=${data.attributes}, cur=${data.attributes.collect { slave.currentValue(it.key) }}"
    return
  }

  def master = getMasterDevice()
  def names = master.currentValue('colorMode') == 'CT' ? ['level', 'colorTemperature'] : ['level', 'hue', 'saturation']
  names.each {
    def prevValue = data.attributes[it]
    def currentValue = master.currentValue(it)
    if (prevValue != currentValue) {
      logTrace "updateDevice - device with dni=${slave.deviceNetworkId} switched On, apply pending ${it}=${currentValue} change"
      updateAttribute(slave, it, currentValue)
    }
  }
}

private updateAttribute(device, attributeName, attributeValue) {
  def convert = ATTRIBUTE_TO_COMMAND[attributeName]
  if (!convert) {
    logTrace "updateAttribute - ${attributeName} isn't supported by app, dni=${device.deviceNetworkId}, skipping update..."
    return
  }

  def command = convert.command
  if (device.hasCommand(command)) {
    def value = convert.eventValueToAttributeValue(attributeValue)
    logInfo "updateAttribute - set ${attributeName}=${value} (event.value=${attributeValue}) using command=${command} for device with dni=${device.deviceNetworkId}"
    device."${command}"(value)
  } else {
    logInfo "updateAttribute - ${attributeName} isn't supported by device with dni=${device.deviceNetworkId}, skipping update..."
  }
  // switch (attributeName) {
  //   case 'level':
  //     if (device.hasCommand('setLevel')) {
  //       logInfo "updateAttribute - set ${attributeName}=${attributeValue} (${toInt(attributeValue)}) for device with dni=${device.deviceNetworkId}"
  //       device.setLevel(toInt(attributeValue))
  //     } else {
  //       logInfo "updateAttribute - ${attributeName} isn't supported by device with dni=${device.deviceNetworkId}"
  //     }
  //     break
  //   case 'colorTemperature':
  //     if (device.hasCommand('setColorTemperature')) device.setColorTemperature(toInt(attributeValue))
  //     break
  //   case 'hue':
  //     if (device.hasCommand('setHue')) device.setHue(toInt(attributeValue))
  //     break
  //   case 'saturation':
  //     if (device.hasCommand('setSaturation')) device.setSaturation(toInt(attributeValue))
  //     break
  // }
}

@Field static Object masterHandlerMutex = new Object()
void masterHandler(event) {
  synchronized (masterHandlerMutex) {
    logInfo "masterHandler name=${event.name} value=${event.value}"
    switch (event.name) {
      case 'switch':
        // commented, as Virtual RGBW Light as a driver will send 'switch=on' on every bulb parameter change
        // if (event.value == 'on') {
        //   slaves?.on()
        // } else {
        //   slaves?.off()
        // }
        break

      case SUPPORTED_ATTRIBUTES:
        def pending = atomicState.pending
        logTrace "masterHandler - prev pending changes: ${pending}"
        slaves?.each { slave ->
          if (slave.currentValue('switch') == 'on') {
            updateAttribute(slave, event.name, event.value)
            logTrace "masterHandler - device with dni=${slave.deviceNetworkId} is 'on', applying attribute ${event.name} with value=${event.value} immediately"
          } else {
            pending = pending.findAll { it.dni != slave.deviceNetworkId }
            pending << [
              dni: slave.deviceNetworkId,
              attributes: SUPPORTED_ATTRIBUTES.collectEntries { [it, slave.currentValue(it)] },
            ]
            logDebug "masterHandler - device with dni=${slave.deviceNetworkId} is 'off', attribute ${event.name} change to value=${event.value} is delayed"
          }
        }
        logTrace "masterHandler - next pending changes: ${pending}"
        atomicState.pending = pending
        break
    }
  }
}
private getMasterDevice() {
  return atomicState.masterDeviceNetworkId != null ? getChildDevice(atomicState.masterDeviceNetworkId) : existingMaster
}
private createMasterDevice(String deviceName) {
  if (atomicState.masterDeviceNetworkId != null && getChildDevice(atomicState.masterDeviceNetworkId)?.deviceNetworkId == deviceName) {
    return
  }
  atomicState.masterDeviceCreationStatus = 'CREATING...'
  deleteMasterDevice()
  try {
    def d = addChildDevice(DRIVER_NAMESPACE, DRIVER_NAME, deviceName, [name: DRIVER_NAME, label: "${deviceName}", isComponent: false])
    atomicState.masterDeviceNetworkId = d.deviceNetworkId
    atomicState.masterDeviceCreationStatus = 'CREATED OK'
    installHandlers(d)
  } catch (e) {
    logError "createChildDevice - Cannot create device, error=${e}"
    atomicState.remove('masterDeviceNetworkId')
    atomicState.masterDeviceCreationStatus = "NOT CREATED, ERROR=${e}"
  }
}
private deleteMasterDevice() {
  getChildDevices().each {
    deleteChildDevice(it.deviceNetworkId)
  }
  atomicState.remove('masterDeviceNetworkId')
}
private toInt(value) {
  new BigDecimal(value).intValue()
}
private getLogsEnabled() { true }
private logTrace(message) { if (logsEnabled) log.trace "${PROJECT_NAME}@${VERSION}: ${message}" }
private logDebug(message) { if (logsEnabled) log.debug "${PROJECT_NAME}@${VERSION}: ${message}" }
private logInfo(message) { if (logsEnabled) log.info "${PROJECT_NAME}@${VERSION}: ${message}" }
private logWarn(message) { log.warn "${PROJECT_NAME}@${VERSION}: ${message}" }
private logError(message) { log.error "${PROJECT_NAME}@${VERSION}: ${message}" }
