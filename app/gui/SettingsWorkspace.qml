import QtQuick 2.9
import QtQuick.Controls 2.5
import QtQuick.Window 2.2

Item {
    id: workspace
    default property alias sections: settingsCards.data
    property string selectedCategory: "all"
    property alias searchText: searchField.text
    readonly property bool compact: width < 860
    readonly property bool searching: searchText.trim().length > 0
    readonly property real contentY: viewport.contentY
    readonly property real contentHeight: viewport.contentHeight
    readonly property int visibleSectionCount: filterState.count
    property var categories: [
        { key: "all", label: qsTr("All settings") },
        { key: "connections", label: qsTr("Connections") },
        { key: "displays", label: qsTr("Displays") },
        { key: "streaming", label: qsTr("Streaming & audio") },
        { key: "input", label: qsTr("Input & controllers") },
        { key: "interface", label: qsTr("Interface") },
        { key: "transfers", label: qsTr("Transfers & clipboard") },
        { key: "diagnostics", label: qsTr("Diagnostics") },
        { key: "updates", label: qsTr("Updates") },
        { key: "advanced", label: qsTr("Advanced") }
    ]
    function focusSearch() { searchField.forceActiveFocus(Qt.TabFocusReason) }
    function categoryIndex() {
        for (var i = 0; i < categories.length; i++) if (categories[i].key === selectedCategory) return i
        return 0
    }
    function chooseCategory(key) {
        selectedCategory = key
        searchText = ""
        applyFilters()
    }
    function collectText(item) {
        var text = ""
        var properties = ["title", "text", "displayText", "placeholderText", "settingsKeywords"]
        for (var i = 0; i < properties.length; i++) {
            var value = item[properties[i]]
            if (typeof value === "string") text += " " + value
        }
        var children = item.children || []
        for (var j = 0; j < children.length; j++) text += " " + collectText(children[j])
        return text.toLowerCase()
    }
    function applyFilters() {
        if (!settingsCards || !filterState || !viewport) return
        var words = searchText.toLowerCase().trim().split(/\s+/).filter(function(word) { return word.length > 0 })
        var count = 0
        var cards = settingsCards.children
        for (var i = 0; i < cards.length; i++) {
            var card = cards[i]
            var tags = card.settingsCategories || []
            var visible = words.length ? true : selectedCategory === "all" || tags.indexOf(selectedCategory) >= 0
            if (words.length) {
                var text = collectText(card)
                for (var j = 0; j < words.length; j++) if (text.indexOf(words[j]) < 0) { visible = false; break }
            }
            card.visible = visible
            if (visible) count++
        }
        filterState.count = count
        autoScroll.stop()
        viewport.contentY = 0
    }
    function revealFocus(item) {
        if (!item || !item.visible) return
        var ancestor = item
        while (ancestor && ancestor !== viewport.contentItem) ancestor = ancestor.parent
        if (!ancestor) return
        var position = item.mapToItem(viewport.contentItem, 0, 0)
        var margin = viewport.height > 100 ? 24 : 0
        var next = viewport.contentY
        if (position.y - margin < next) next = position.y - margin
        else if (position.y + item.height + margin > next + viewport.height) next = position.y + item.height + margin - viewport.height
        autoScroll.stop()
        autoScroll.from = viewport.contentY
        autoScroll.to = Math.max(0, Math.min(next, Math.max(0, viewport.contentHeight - viewport.height)))
        autoScroll.start()
    }
    onSelectedCategoryChanged: applyFilters()
    onSearchTextChanged: applyFilters()
    Component.onCompleted: applyFilters()
    Window.onActiveFocusItemChanged: revealFocus(Window.activeFocusItem)

    data: [
        QtObject { id: filterState; property int count: 0 },
        NumberAnimation { id: autoScroll; target: viewport; property: "contentY"; duration: 100 },
        Rectangle {
            id: header
            width: parent.width
            height: workspace.compact ? 164 : 116
            color: "#0B1120"
            Column {
                anchors.fill: parent; anchors.margins: 16; spacing: 10
                Row {
                    width: parent.width; spacing: 12
                    Label { text: qsTr("Settings"); color: "#E2E8F0"; font.pixelSize: 24; font.bold: true }
                    Label { text: qsTr("Your global defaults"); color: "#94A3B8"; anchors.verticalCenter: parent.verticalCenter; visible: workspace.width >= 440 }
                }
                TextField {
                    id: searchField
                    objectName: "settingsSearch"
                    width: parent.width
                    placeholderText: qsTr("Search all settings")
                    selectByMouse: true
                    Accessible.name: qsTr("Search all settings")
                    Keys.onEscapePressed: text = ""
                }
                ComboBox {
                    id: compactCategories
                    objectName: "settingsCategoryPicker"
                    visible: workspace.compact; width: parent.width
                    model: workspace.categories; textRole: "label"
                    currentIndex: workspace.categoryIndex()
                    Accessible.name: qsTr("Settings section")
                    onActivated: workspace.chooseCategory(workspace.categories[currentIndex].key)
                }
            }
        },
        Rectangle {
            id: sidebar
            visible: !workspace.compact
            width: 200
            anchors.top: header.bottom; anchors.bottom: parent.bottom; anchors.left: parent.left
            color: "#0B1120"
            ListView {
                anchors.fill: parent; anchors.margins: 12; spacing: 4; clip: true
                model: workspace.categories
                ScrollBar.vertical: ScrollBar {}
                delegate: ItemDelegate {
                    id: categoryButton
                    width: ListView.view.width; height: 42
                    text: modelData.label
                    leftPadding: 14; rightPadding: 10
                    font.pixelSize: 14
                    hoverEnabled: true
                    contentItem: Text {
                        text: categoryButton.text; textFormat: Text.PlainText
                        font: categoryButton.font; verticalAlignment: Text.AlignVCenter
                        color: categoryButton.highlighted ? "#C7D2FE" : "#CBD5E1"
                        elide: Text.ElideRight
                    }
                    background: Rectangle {
                        radius: 8
                        color: categoryButton.highlighted ? "#1D2948" : categoryButton.hovered ? "#121D31" : "transparent"
                        border.width: categoryButton.activeFocus ? 2 : 1
                        border.color: categoryButton.activeFocus ? "#818CF8" : categoryButton.highlighted ? "#465AB5" : "transparent"
                    }
                    highlighted: !workspace.searching && workspace.selectedCategory === modelData.key
                    Accessible.name: modelData.label
                    onClicked: workspace.chooseCategory(modelData.key)
                    onActiveFocusChanged: if (activeFocus) ListView.view.positionViewAtIndex(index, ListView.Contain)
                }
            }
        },
        Flickable {
            id: viewport
            objectName: "settingsViewport"
            anchors.top: header.bottom; anchors.bottom: parent.bottom
            anchors.left: parent.left; anchors.leftMargin: workspace.compact ? 0 : sidebar.width
            anchors.right: parent.right
            clip: true; boundsBehavior: Flickable.StopAtBounds
            contentWidth: width
            contentHeight: settingsCards.height + 64
            ScrollBar.vertical: ScrollBar {}
            Label {
                id: resultsLabel
                x: 16; y: 10; width: Math.max(1, parent.width - 32)
                text: workspace.searching ? qsTr("Search results · %1 sections").arg(filterState.count) : workspace.categories[workspace.categoryIndex()].label
                font.pixelSize: 13; font.bold: true; color: "#A5B4FC"
            }
            Label {
                objectName: "settingsNoResults"
                x: 16; y: 42; width: Math.max(1, viewport.width - 32)
                visible: filterState.count === 0
                text: qsTr("No settings match. Try another search or choose All settings.")
                color: "#CBD5E1"; wrapMode: Text.WordWrap
            }
            Column {
                id: settingsCards
                x: Math.max(16, (viewport.width - width) / 2); y: 40
                width: Math.max(1, Math.min(920, viewport.width - 32))
                spacing: 20
                onChildrenChanged: Qt.callLater(workspace.applyFilters)
            }
        }
    ]
}
