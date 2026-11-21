import QtQuick 2.9
import QtQuick.Controls 2.5

NavigableMenuItem {
    id: control
    property string description
    width: menu ? menu.availableWidth : 280
    Accessible.description: description
    Binding {
        target: control.indicator
        property: "y"
        when: control.indicator !== null
        value: control.indicator ? control.topPadding + (titleLabel.implicitHeight - control.indicator.height) / 2 : 0
    }
    contentItem: Item {
        readonly property real indicatorPadding: control.checkable && control.indicator ? control.indicator.width + control.spacing : 0
        implicitWidth: textColumn.implicitWidth + indicatorPadding
        implicitHeight: textColumn.implicitHeight
        Column {
            id: textColumn
            x: control.mirrored ? 0 : parent.indicatorPadding
            width: Math.max(0, parent.width - parent.indicatorPadding)
            spacing: 4
            Label {
                id: titleLabel
                width: parent.width
                text: control.text
                font: control.font
                wrapMode: Text.Wrap
            }
            Label {
                objectName: "menuDescription"
                width: parent.width
                visible: text.length > 0
                text: control.description
                font.pointSize: Math.max(9, control.font.pointSize - 1)
                opacity: 0.8
                wrapMode: Text.Wrap
            }
        }
    }
}
