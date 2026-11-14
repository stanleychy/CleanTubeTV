package io.github.stanleychy.cleantubetv.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.stanleychy.cleantubetv.R
import io.github.stanleychy.cleantubetv.app
import io.github.stanleychy.cleantubetv.remote.RemoteKeyService
import io.github.stanleychy.cleantubetv.settings.Settings
import io.github.stanleychy.cleantubetv.yt.AccountInfo
import io.github.stanleychy.cleantubetv.yt.FeedItemsMapper
import io.github.stanleychy.cleantubetv.yt.FeedPage
import io.github.stanleychy.cleantubetv.yt.FeedParser
import io.github.stanleychy.cleantubetv.yt.SearchParser

class MainActivity : Activity() {
    private enum class Tab(val browseId: String?) {
        HOME("default"), SUBSCRIPTIONS("FEsubscriptions"), SEARCH(null), SETTINGS(null);

        /** True for a tab that shows videos in the grid. */
        val hasVideos get() = this != SETTINGS
    }

    private lateinit var messagePanel: View
    private lateinit var status: TextView
    private lateinit var actionButton: TextView
    private lateinit var grid: RecyclerView
    private lateinit var settingsPanel: View
    private lateinit var tabs: Map<Tab, View>
    private lateinit var adapter: FeedAdapter

    private var current = Tab.HOME

    /** The query of the Search tab. The app does not store it. */
    private var query = ""
    private lateinit var searchBack: BackOverride
    private var continuation: String? = null
    private var loading = false
    private var generation = 0
    private var account: AccountInfo? = null
    private lateinit var remoteRow: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        messagePanel = findViewById(R.id.message_panel)
        status = findViewById(R.id.status)
        actionButton = findViewById(R.id.action_button)
        actionButton.setText(R.string.sign_in)
        actionButton.setOnClickListener { startActivity(Intent(this, SignInActivity::class.java)) }
        grid = findViewById(R.id.grid)
        settingsPanel = findViewById(R.id.settings_panel)
        setUpSettings()
        tabs = mapOf(
            Tab.HOME to findViewById(R.id.tab_home),
            Tab.SUBSCRIPTIONS to findViewById(R.id.tab_subs),
            Tab.SEARCH to findViewById(R.id.tab_search),
            Tab.SETTINGS to findViewById(R.id.tab_settings),
        )
        // The search field has its own click listener: OK opens the keyboard.
        tabs.forEach { (tab, view) -> if (tab != Tab.SEARCH) view.setOnClickListener { select(tab) } }
        SearchField(findViewById(R.id.tab_search)) { search(it) }
        searchBack = BackOverride(this) { leaveSearch() }

        adapter = FeedAdapter(
            onClick = { v -> startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_ID, v.id).putExtra(PlayerActivity.EXTRA_TITLE, v.title)) },
            onNearEnd = { loadMore() },
        )
        val lm = GridLayoutManager(this, COLUMNS)
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (adapter.isHeader(position)) COLUMNS else 1
        }
        grid.layoutManager = lm
        grid.adapter = adapter
        select(Tab.HOME)
    }

    override fun onResume() {
        super.onResume()
        // Refresh after sign-in.
        if (current == Tab.SETTINGS) showAccount()
        // The user can turn the service on or off in the system settings.
        refreshRemoteRow()
        if (current.hasVideos && app.auth.isSignedIn && adapter.itemCount == 0 && !loading) {
            load(current, null)
        }
    }

    /** Before Android 16, the system calls this function for Back. Refer to [BackOverride] for Android 16 and later. */
    @Deprecated("Deprecated in Java")
    @SuppressLint("GestureBackNavigation") // BackOverride does this task on Android 16 and later.
    override fun onBackPressed() {
        if (current == Tab.SEARCH) return leaveSearch()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    /** Shows the results for a query that the user entered in the search field. */
    private fun search(text: String) {
        query = text
        select(Tab.SEARCH)
    }

    /** Back from the search results goes to Home. A second Back closes the app, as on the other tabs. */
    private fun leaveSearch() = select(Tab.HOME)

    private fun select(tab: Tab) {
        current = tab
        searchBack.enabled = tab == Tab.SEARCH
        tabs.forEach { (t, v) -> v.isSelected = t == tab }
        generation++
        continuation = null
        loading = false
        adapter.set(emptyList())
        when (tab) {
            Tab.SETTINGS -> showSettings()
            else -> load(tab, null)
        }
    }

    /** Shows one content view and hides the others. */
    private fun show(view: View) {
        for (v in listOf(messagePanel, grid, settingsPanel)) v.visibility = if (v == view) View.VISIBLE else View.GONE
    }

    private fun showMessage(text: String, withSignIn: Boolean = false) {
        show(messagePanel)
        status.text = text
        actionButton.visibility = if (withSignIn) View.VISIBLE else View.GONE
    }

    private fun showSettings() {
        show(settingsPanel)
        showAccount()
    }

    /** Fills the account section at the top of the Settings panel. Asks YouTube for the account only one time. */
    private fun showAccount() {
        val photo = findViewById<ImageView>(R.id.account_photo)
        val name = findViewById<TextView>(R.id.account_name)
        val detail = findViewById<TextView>(R.id.account_detail)
        val button = findViewById<TextView>(R.id.account_button)
        if (!app.auth.isSignedIn) {
            account = null
            photo.visibility = View.GONE
            name.setText(R.string.not_signed_in)
            detail.setText(R.string.not_signed_in_detail)
            button.setText(R.string.sign_in)
            button.setOnClickListener { startActivity(Intent(this, SignInActivity::class.java)) }
            return
        }
        button.setText(R.string.sign_out)
        button.setOnClickListener {
            app.auth.signOut()
            showAccount()
        }
        val known = account
        if (known != null) {
            name.text = known.name
            detail.text = known.byline ?: known.handle.orEmpty()
            photo.visibility = if (known.photoUrl != null) View.VISIBLE else View.GONE
            ImageLoader.load(photo, known.photoUrl)
            return
        }
        photo.visibility = View.GONE
        name.setText(R.string.account_unknown)
        detail.setText(R.string.loading)
        app.io.execute {
            val result = runCatching { app.innerTube.account() }
            runOnUiThread {
                result.onFailure { Log.w(TAG, "account request failed", it) }
                val info = result.getOrNull()
                if (info == null) {
                    if (current == Tab.SETTINGS) detail.setText(R.string.account_load_error)
                } else {
                    account = info
                    if (current == Tab.SETTINGS) showAccount()
                }
            }
        }
    }

    private fun setUpSettings() {
        val s = app.settings
        val history = findViewById<TextView>(R.id.setting_history)
        val speeds = findViewById<TextView>(R.id.setting_speeds)
        val audio = findViewById<TextView>(R.id.setting_audio)
        val subtitles = findViewById<TextView>(R.id.setting_subtitles)
        remoteRow = findViewById(R.id.setting_remote)
        remoteRow.setOnClickListener { setUpRemoteKey() }
        findViewById<TextView>(R.id.setting_cast).setOnClickListener { startActivity(Intent(this, CastActivity::class.java)) }
        val pkg = packageManager.getPackageInfo(packageName, 0)
        findViewById<TextView>(R.id.setting_version).text =
            getString(R.string.setting_version, pkg.versionName, pkg.longVersionCode, getString(R.string.build_commit))
        fun refresh() {
            history.text = getString(R.string.setting_history, getString(if (s.historyEnabled) R.string.on else R.string.off))
            speeds.text = getString(R.string.setting_speeds, s.speeds.joinToString(", ") { Settings.speedLabel(it) })
            audio.text = getString(
                R.string.setting_audio,
                s.audioLanguage.ifEmpty { null }?.let { Settings.languageName(it) } ?: getString(R.string.original_audio),
            )
            subtitles.text = getString(
                R.string.setting_subtitles,
                s.subtitleLanguage.ifEmpty { null }?.let { Settings.languageName(it) } ?: getString(R.string.off),
            )
            refreshRemoteRow()
        }
        history.setOnClickListener {
            s.historyEnabled = !s.historyEnabled
            refresh()
        }
        speeds.setOnClickListener {
            val choices = Settings.SPEED_CHOICES
            val checked = BooleanArray(choices.size) { choices[it] in s.speeds }
            dialog().setTitle(R.string.pick_speeds)
                .setMultiChoiceItems(choices.map { Settings.speedLabel(it) }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val picked = choices.filterIndexed { i, _ -> checked[i] }
                    s.speeds = picked.ifEmpty { Settings.DEFAULT_SPEEDS }
                    refresh()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        audio.setOnClickListener {
            pickLanguage(R.string.pick_audio, getString(R.string.original_audio), s.audioLanguage) {
                s.audioLanguage = it
                refresh()
            }
        }
        subtitles.setOnClickListener {
            pickLanguage(R.string.pick_subtitles, getString(R.string.off), s.subtitleLanguage) {
                s.subtitleLanguage = it
                refresh()
            }
        }
        refresh()
    }

    private fun refreshRemoteRow() {
        val code = app.settings.remoteKeyCode
        val state = when {
            !RemoteKeyService.isEnabled(this) -> getString(R.string.remote_service_off)
            code == KeyEvent.KEYCODE_UNKNOWN -> getString(R.string.remote_not_set)
            else -> getString(R.string.remote_key, KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_"))
        }
        remoteRow.text = getString(R.string.setting_remote, state)
        // The cast row is refreshed with the remote row: both can change while a different screen is open.
        findViewById<TextView>(R.id.setting_cast).text = getString(
            R.string.setting_cast,
            getString(if (app.cast.isLinked) R.string.cast_state_linked else R.string.cast_state_not_linked),
        )
    }

    /** Asks the user to turn on the service, or saves the next key that the service gets. */
    private fun setUpRemoteKey() {
        if (!RemoteKeyService.isEnabled(this)) {
            val component = ComponentName(this, RemoteKeyService::class.java).flattenToShortString()
            dialog().setTitle(R.string.remote_off_title)
                .setMessage(getString(R.string.remote_off_message, component))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val d = dialog().setTitle(R.string.remote_learn_title)
            .setMessage(R.string.remote_learn_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.remote_clear) { _, _ ->
                app.settings.remoteKeyCode = KeyEvent.KEYCODE_UNKNOWN
                refreshRemoteRow()
            }
            .create()
        d.setOnDismissListener { RemoteKeyService.learner = null }
        RemoteKeyService.learner = { code ->
            runOnUiThread {
                app.settings.remoteKeyCode = code
                refreshRemoteRow()
                d.setOnDismissListener(null)
                d.dismiss()
            }
        }
        d.show()
    }

    /** A single-choice list: [emptyLabel] (stored as "") and the common languages. */
    private fun pickLanguage(title: Int, emptyLabel: String, currentTag: String, onPick: (String) -> Unit) {
        val tags = listOf("") + Settings.languageChoices()
        val labels = tags.map { if (it.isEmpty()) emptyLabel else Settings.languageName(it) }.toTypedArray()
        dialog().setTitle(title)
            .setSingleChoiceItems(labels, tags.indexOf(currentTag).coerceAtLeast(0)) { d, i ->
                onPick(tags[i])
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun load(tab: Tab, token: String?) {
        if (loading) return
        if (!app.auth.isSignedIn) return showMessage(getString(R.string.sign_in_first), withSignIn = true)
        loading = true
        val gen = generation
        if (token == null) showMessage(getString(R.string.loading))
        if (tab == Tab.SEARCH) return loadSearch(token, gen)
        app.io.execute {
            val result = runCatching {
                val resp = app.innerTube.browse(tab.browseId, token)
                FeedParser.parse(resp, debug = app.debuggable)
            }
            runOnUiThread { showPage(result, token, gen) }
        }
    }

    /**
     * Gets one page of search results. The thread gets a copy of the query, because the user can change it.
     * The history setting applies to search also: when it is off, the search goes out with no account.
     */
    private fun loadSearch(token: String?, gen: Int) {
        val text = query
        val withAccount = app.settings.historyEnabled
        app.io.execute {
            val result = runCatching { SearchParser.parse(app.innerTube.search(text, token, withAccount)) }
            runOnUiThread { showPage(result, token, gen) }
        }
    }

    /** Shows a first page (no [token]) or adds a next page. Ignores a reply for a tab that the user left. */
    private fun showPage(result: Result<FeedPage>, token: String?, gen: Int) {
        if (gen != generation) return
        loading = false
        val page = result.getOrElse { e ->
            Log.w(TAG, "load failed", e)
            if (token == null) showMessage(getString(R.string.load_error, e.message))
            return
        }
        continuation = page.continuation
        val items = FeedItemsMapper.toItems(page)
        if (token != null) return adapter.append(items)
        if (items.isEmpty()) return showMessage(getString(R.string.empty_feed))
        show(grid)
        adapter.set(items)
        grid.post { focusFirstCard() }
    }

    /** Moves the focus to the first video. A shelf header is not focusable, and search results have no header. */
    private fun focusFirstCard() {
        val first = (0 until grid.childCount).map { grid.getChildAt(it) }.firstOrNull { it.isFocusable }
        first?.requestFocus()
    }

    private fun loadMore() {
        val token = continuation ?: return
        if (!current.hasVideos) return
        continuation = null
        load(current, token)
    }

    private companion object {
        const val TAG = "MainActivity"
        const val COLUMNS = 4
    }
}
