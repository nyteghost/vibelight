import QtQuick 2.9
import QtQuick.Controls 2.5

// Preserve the native indicator, focus and click handling; only wrap its label.
CheckBox {
    function wrapLabel() {
        if (contentItem && typeof contentItem.wrapMode !== "undefined") {
            contentItem.wrapMode = Text.WordWrap
            contentItem.elide = Text.ElideNone
        }
    }
    Component.onCompleted: wrapLabel()
    onContentItemChanged: wrapLabel()
}
