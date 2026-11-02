package io.github.stanleychy.cleantubetv.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.stanleychy.cleantubetv.R
import io.github.stanleychy.cleantubetv.app
import io.github.stanleychy.cleantubetv.settings.Settings
import io.github.stanleychy.cleantubetv.yt.AccountInfo
import io.github.stanleychy.cleantubetv.yt.FeedItemsMapper
import io.github.stanleychy.cleantubetv.yt.FeedParser

class MainActivity : Activity() {
    private enum class Tab(val browseId: String?) {
        HOME("default"), SUBSCRIPTIONS("FEsubscriptions"), ACCOUNT(null), SETTINGS(null)
    }

    private lateinit var messagePanel: View
    private lateinit var status: TextView
    private lateinit var actionButton: TextView
    private lateinit var grid: RecyclerView
    private lateinit var accountPanel: View
    private lateinit var settingsPanel: View
    private lateinit var tabs: Map<Tab, TextView>
    private lateinit var adapter: FeedAdapter

    private var current = Tab.HOME
    private var continuation: String? = null
    private var loading = false
    private var generation = 0
    private var account: AccountInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        messagePanel = findViewById(R.id.message_panel)
        status = findViewById(R.id.status)
        actionButton = findViewById(R.id.action_button)
        actionButton.setText(R.string.sign_in)
        actionButton.setOnClickListener { startActivity(Intent(this, SignInActivity::class.java)) }
        grid = findViewById(R.id.grid)
        accountPanel = findViewById(R.id.account_panel)
        settingsPanel = findViewById(R.id.settings_panel)
        setUpSettings()
        tabs = mapOf(
            Tab.HOME to findViewById(R.id.tab_home),
            Tab.SUBSCRIPTIONS to findViewById(R.id.tab_subs),
            Tab.ACCOUNT to findViewById(R.id.tab_account),
            Tab.SETTINGS to findViewById(R.id.tab_settings),
        )
        tabs.forEach { (tab, view) -> view.setOnClickListener { select(tab) } }

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
        if (current == Tab.ACCOUNT) showAccount()
        if ((current == Tab.HOME || current == Tab.SUBSCRIPTIONS) && app.auth.isSignedIn && adapter.itemCount == 0 && !loading) {
            load(current, null)
        }
    }

    private fun select(tab: Tab) {
        current = tab
        tabs.forEach { (t, v) -> v.isSelected = t == tab }
        generation++
        continuation = null
        loading = false
        adapter.set(emptyList())
        when (tab) {
            Tab.ACCOUNT -> showAccount()
            Tab.SETTINGS -> show(settingsPanel)
            else -> load(tab, null)
        }
    }

    /** Shows one content view and hides the others. */
    private fun show(view: View) {
        for (v in listOf(messagePanel, grid, accountPanel, settingsPanel)) v.visibility = if (v == view) View.VISIBLE else View.GONE
    }

    private fun showMessage(text: String, withSignIn: Boolean = false) {
        show(messagePanel)
        status.text = text
        actionButton.visibility = if (withSignIn) View.VISIBLE else View.GONE
    }

    private fun showAccount() {
        show(accountPanel)
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
                    if (current == Tab.ACCOUNT) detail.setText(R.string.account_load_error)
                } else {
                    account = info
                    if (current == Tab.ACCOUNT) showAccount()
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
        app.io.execute {
            val result = runCatching {
                val resp = app.innerTube.browse(tab.browseId, token)
                FeedParser.parse(resp)
            }
            runOnUiThread {
                if (gen != generation) return@runOnUiThread
                loading = false
                result.onSuccess { page ->
                    continuation = page.continuation
                    val items = FeedItemsMapper.toItems(page)
                    if (token == null) {
                        if (items.isEmpty()) {
                            showMessage(getString(R.string.empty_feed))
                        } else {
                            show(grid)
                            adapter.set(items)
                            grid.post { grid.getChildAt(1)?.requestFocus() ?: grid.getChildAt(0)?.requestFocus() }
                        }
                    } else {
                        adapter.append(items)
                    }
                }.onFailure { e ->
                    Log.w(TAG, "browse failed", e)
                    if (token == null) showMessage(getString(R.string.load_error, e.message))
                }
            }
        }
    }

    private fun loadMore() {
        val token = continuation ?: return
        if (current.browseId == null) return
        continuation = null
        load(current, token)
    }

    private companion object {
        const val TAG = "MainActivity"
        const val COLUMNS = 4
    }
}
