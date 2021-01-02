/**
 *  ALL MY LATEST HUBITAT APPS AND DRIVERS: https://github.com/ibezkrovnyi/hubitat
 *
 *  ==== Smarter Bulb (Parent App) ========================
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
 *  1.0.1 - 2020-01-02 - Initial release
 */
import groovy.transform.Field

@Field static String PROJECT_NAME = 'Smarter Bulb'
@Field static String PROJECT_NAMESPACE = 'ibezkrovnyi'
@Field static String DRIVER_NAME = 'Virtual RGBW Light'
@Field static String DRIVER_NAMESPACE = 'hubitat'
@Field static String CATEGORY = 'My Apps'

definition(
  name: "Virtual - ${PROJECT_NAME} (Parent)",
  namespace: PROJECT_NAMESPACE,
  author: 'Igor Bezkrovnyi',
  description: "Group Bulbs and change their dimming level, color temperature and color without triggering bulb ON",
  category: CATEGORY,
  filename: 'virtual-smarter-bulb-parent-app.groovy',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  importUrl: '',
)

preferences {
  page name: 'configurationPage', title: app.label, install: true, uninstall: true
}

void installed() {
  logDebug "Installed with settings: ${settings}"
  initialize()
}

void updated() {
  logDebug "Updated with settings: ${settings}"
  unsubscribe()
  initialize()
}

void initialize() {
  logInfo "There are ${childApps.size()} child apps"
  childApps.each { child ->
    logInfo "Child app: ${child.label} ${child.id}"
  }
}

def configurationPage() {
  dynamicPage(name: 'configurationPage') {
    if (app.getInstallationState() != 'COMPLETE') {
      section { paragraph "Click 'Done' to install <b>${app.label}</b> parent app" }
    } else {
      section('Description', hideable: true, hidden: true) {
        paragraph 'TBD'
      }
      section('Child Apps') {
        app(name: 'anyOpenApp', appName: "Virtual - ${PROJECT_NAME} (Child)", namespace: PROJECT_NAMESPACE, title: "Create <b>NEW</b> Virtual ${PROJECT_NAME} Group", multiple: true)
      }
      section('General') {
        label title: '<b>PARENT APP NAME</b> (optional)', required: false
      }
    }
  }
}
private logDebug(message) { log.debug "${app.label}: ${message}" }
private logInfo(message) { log.info "${app.label}: ${message}" }
private logWarn(message) { log.warn "${app.label}: ${message}" }
private logError(message) { log.error "${app.label}: ${message}" }
