import QtQuick 2.9
import QtQuick.Controls 2.5
import QtQuick.Window 2.2
import QtTest 1.2
import "../../app/gui"

Item {
    id: scene
    width: 1100; height: 760
    SettingsWorkspace {
        id: settings
        anchors.fill: parent
        CardGroupBox {
            id: displays
            property var settingsCategories: ["displays"]
            width: parent.width; title: "Displays"
            Column {
                Label { text: "Resolution and frame rate" }
                WrappingCheckBox { id: untouched; checked: true; text: "Use native resolution"; property int changes: 0; onClicked: changes++ }
            }
        }
        CardGroupBox {
            id: streaming
            property var settingsCategories: ["streaming"]
            width: parent.width; title: "Streaming quality"
            Column { Label { text: "Video bitrate ceiling" } }
        }
        CardGroupBox {
            id: advanced
            property var settingsCategories: ["advanced"]
            width: parent.width; title: "Advanced decoding"
            Column { Label { text: "Hardware decoder" } }
        }
    }
    TestCase {
        name: "SettingsWorkspace"
        when: windowShown
        function init() {
            scene.width = 1100
            scene.height = 760
            settings.Window.window.width = 1100
            settings.Window.window.height = 760
            settings.chooseCategory("all")
            tryCompare(settings, "visibleSectionCount", 3)
        }
        function test_categorySwitchPreservesControls() {
            settings.chooseCategory("displays")
            tryCompare(settings, "visibleSectionCount", 1)
            verify(displays.visible)
            verify(!streaming.visible)
            verify(!advanced.visible)
            settings.chooseCategory("advanced")
            verify(advanced.visible)
            settings.chooseCategory("all")
            verify(untouched.checked)
            compare(untouched.changes, 0)
        }
        function test_searchCrossesCategoriesAndClearRestoresSelection() {
            settings.chooseCategory("displays")
            settings.searchText = "video bitrate"
            tryCompare(settings, "visibleSectionCount", 1)
            verify(streaming.visible)
            verify(!displays.visible)
            settings.searchText = ""
            verify(displays.visible)
            verify(!streaming.visible)
            compare(settings.selectedCategory, "displays")
            compare(untouched.changes, 0)
        }
        function test_noResultsAndEscapeClear() {
            settings.searchText = "not-present-anywhere"
            tryCompare(settings, "visibleSectionCount", 0)
            var search = findChild(settings, "settingsSearch")
            settings.focusSearch()
            verify(search.activeFocus)
            keyClick(Qt.Key_Escape)
            compare(settings.searchText, "")
            tryCompare(settings, "visibleSectionCount", 3)
        }
        function test_compactNavigation() {
            scene.width = 420
            scene.height = 480
            settings.Window.window.width = 420
            settings.Window.window.height = 480
            tryCompare(settings, "compact", true)
            var picker = findChild(settings, "settingsCategoryPicker")
            verify(picker.visible)
            settings.chooseCategory("advanced")
            tryCompare(picker, "currentIndex", 9)
            verify(advanced.visible)
            verify(advanced.width <= settings.width)
            verify(picker.width <= settings.width)
        }
    }
}
