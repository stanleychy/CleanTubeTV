package io.github.stanleychy.cleantubetv.ui

import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * The search field in the tab bar, for a TV remote:
 * - OK opens the on-screen keyboard.
 * - The search action of the keyboard calls [onSearch] with the query and closes the keyboard.
 * - Left and Right always move the focus to the adjacent tab. Without this, the remote must move
 *   the text cursor to the end of the query before the focus can leave the field.
 * The field keeps no search history and shows no suggestions.
 */
class SearchField(private val field: EditText, private val onSearch: (String) -> Unit) {
    private val keyboard = field.context.getSystemService(InputMethodManager::class.java)

    init {
        field.setOnClickListener { showKeyboard() }
        field.setOnEditorActionListener { _, actionId, event -> onEditorAction(actionId, event) }
        field.setOnKeyListener { _, keyCode, event -> moveFocus(keyCode, event) }
    }

    private fun onEditorAction(actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) return submit()
        if (event?.keyCode != KeyEvent.KEYCODE_ENTER && event?.keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        // Some remotes send Enter for OK. Thus Enter opens the keyboard first, and starts the search only from the keyboard.
        if (keyboardVisible()) submit() else showKeyboard()
        return true
    }

    /** Starts the search. An empty query does nothing, and the keyboard stays open. */
    private fun submit(): Boolean {
        val query = field.text.toString().trim()
        if (query.isEmpty()) return true
        keyboard.hideSoftInputFromWindow(field.windowToken, 0)
        onSearch(query)
        return true
    }

    private fun moveFocus(keyCode: Int, event: KeyEvent): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) field.focusSearch(direction)?.requestFocus()
        return true
    }

    private fun showKeyboard() {
        keyboard.showSoftInput(field, 0)
    }

    private fun keyboardVisible() = field.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
}
