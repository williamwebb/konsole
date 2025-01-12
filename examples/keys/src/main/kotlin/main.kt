import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Keys
import com.varabyte.kotter.foundation.input.onKeyPressed
import com.varabyte.kotter.foundation.input.runUntilKeyPressed
import com.varabyte.kotter.foundation.liveVarOf
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.text.textLine

fun main() = session {
    var keyName by liveVarOf("")
    section {
        textLine("Press any key (and Q will quit)")
        textLine()
        textLine("Last key pressed: $keyName")
    }.runUntilKeyPressed(Keys.Q) {
        onKeyPressed {
            keyName = when (key) {
                Keys.ESC -> "ESC"
                Keys.ENTER -> "ENTER"
                Keys.BACKSPACE -> "BACKSPACE"
                Keys.DELETE -> "DELETE"
                Keys.EOF -> "EOF"
                Keys.UP -> "UP"
                Keys.DOWN -> "DOWN"
                Keys.LEFT -> "LEFT"
                Keys.RIGHT -> "RIGHT"
                Keys.HOME -> "HOME"
                Keys.END -> "END"
                Keys.INSERT -> "INSERT"
                Keys.PAGE_UP -> "PAGE_UP"
                Keys.PAGE_DOWN -> "PAGE_DOWN"
                Keys.TAB -> "TAB"
                is CharKey -> (key as CharKey).code.toString()
                else -> "Unhandled key. Please report a bug!"
           }
        }
    }
}