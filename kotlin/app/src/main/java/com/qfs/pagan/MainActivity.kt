package com.qfs.pagan

//import com.qfs.radixulous.MIDIPlaybackDevice

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.qfs.pagan.apres.*
import com.qfs.pagan.databinding.ActivityMainBinding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread
import com.qfs.pagan.InterfaceLayer as OpusManager

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private lateinit var midi_controller: MIDIController
    lateinit var midi_playback_device: MIDIPlaybackDevice
    private var midi_input_device = VirtualMIDIDevice()
    private var midi_player = MIDIPlayer()
    lateinit var soundfont: SoundFont

    private var opus_manager = OpusManager(this)

    private var in_play_back: Boolean = false

    private lateinit var optionsMenu: Menu
    internal lateinit var project_manager: ProjectManager
    private var progressBar: ProgressBar? = null

    private var number_selector_defaults = HashMap<String, Int>()

    private var export_project_intent_launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val opus_manager = this.get_opus_manager()
            result?.data?.data?.also { uri ->
                applicationContext.contentResolver.openFileDescriptor(uri, "w")?.use {
                    val json_string = Json.encodeToString(opus_manager.to_json())
                    FileOutputStream(it.fileDescriptor).write(json_string.toByteArray())
                    this.feedback_msg("Exported")
                }
            }
        }
    }

    var import_project_intent_launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result?.data?.data?.also { uri ->
                val fragment = this.getActiveFragment()
                fragment?.setFragmentResult("IMPORTPROJECT", bundleOf(Pair("URI", uri.toString())))
                this.navTo("main")
            }
        }
    }

    private var export_midi_intent_launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val opus_manager = this.get_opus_manager()
            result?.data?.data?.also { uri ->
                applicationContext.contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).write(opus_manager.get_midi().as_bytes())
                    this.feedback_msg("Exported to midi")
                }
            }
        }
    }

    var import_midi_intent_launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result?.data?.data?.also { uri ->
                val fragment = this.getActiveFragment()
                fragment?.setFragmentResult("IMPORT", bundleOf(Pair("URI", uri.toString())))
                this.navTo("main")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.project_manager = ProjectManager(applicationInfo.dataDir)
        this.binding = ActivityMainBinding.inflate(this.layoutInflater)
        setContentView(this.binding.root)
        setSupportActionBar(this.binding.appBarMain.toolbar)
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        this.appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, this.appBarConfiguration)

        this.lockDrawer()
        //////////////////////////////////////////
        // TODO: clean up the file -> riff -> soundfont -> midi playback device process

        this.soundfont = SoundFont(assets, "FluidR3_GM.sf2")
        this.midi_playback_device = MIDIPlaybackDevice(this, this.soundfont)

        this.midi_controller = MIDIController(window.decorView.rootView.context)
        this.midi_controller.registerVirtualDevice(this.midi_playback_device)
        this.midi_controller.registerVirtualDevice(this.midi_input_device)
        this.midi_controller.registerVirtualDevice(this.midi_player)
        ///////////////////////////////////////////
    }

    fun update_channel_instruments(opus_manager: OpusManager) {
        for (channel in opus_manager.channels) {
            this.midi_input_device.sendEvent(ProgramChange(channel.midi_channel, channel.midi_instrument))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this.menuInflater.inflate(R.menu.main_options_menu, menu)
        this.optionsMenu = menu
        val output = super.onCreateOptionsMenu(menu)
        this.update_menu_options()
        return output
    }

    fun save_dialog(callback: () -> Unit) {
        if (this.get_opus_manager().has_changed_since_save()) {
            val that = this
            AlertDialog.Builder(this, R.style.AlertDialog).apply {
                setTitle("Save Current Project First?")
                setCancelable(true)
                setPositiveButton("Yes") { dialog, _ ->
                    that.save_current_project()
                    dialog.dismiss()
                    callback()
                }
                setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                    callback()
                }
                show()
            }
        } else {
            callback()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.itmNewProject -> {
                // TODO: Save or discard popup dialog
                this.save_dialog {
                    val fragment = this.getActiveFragment()
                    fragment?.setFragmentResult("NEW", bundleOf())
                    this.navTo("main")
                }
            }
            R.id.itmLoadProject -> {
                this.save_dialog {
                    this.navTo("load")
                }
            }
            R.id.itmImportMidi -> {
                this.save_dialog {
                    val intent = Intent()
                        .setType("*/*")
                        .setAction(Intent.ACTION_GET_CONTENT)
                    this.import_midi_intent_launcher.launch(intent)
                }
            }
            R.id.itmUndo -> {
                this.get_opus_manager().apply_undo()
            }
            R.id.itmPlay -> {
                this.playback_dialog()
            }
            R.id.itmImportProject -> {
                this.save_dialog {
                    val intent = Intent()
                        .setType("application/json")
                        .setAction(Intent.ACTION_GET_CONTENT)
                    this.import_project_intent_launcher.launch(intent)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun setup_config_drawer() {
        val opus_manager = this.get_opus_manager()
        val rvActiveChannels: RecyclerView = this.findViewById(R.id.rvActiveChannels)
        ChannelOptionAdapter(this, opus_manager, rvActiveChannels, this.soundfont)

        val tvChangeProjectName: TextView = this.findViewById(R.id.tvChangeProjectName)
        tvChangeProjectName.setOnClickListener {
            this.change_name_dialog()
        }

        val tvTempo: TextView = this.findViewById(R.id.tvTempo)
        tvTempo.text = "${opus_manager.tempo.toInt()} BPM"
        tvTempo.setOnClickListener {
            this.popup_number_dialog("Set Tempo (BPM)", 1, 999, opus_manager.tempo.toInt()) { tempo: Int ->
                opus_manager.set_tempo(tempo.toFloat())
            }
        }

        val btnTranspose: TextView = this.findViewById(R.id.btnTranspose)
        btnTranspose.text = "T: ${opus_manager.transpose}"
        btnTranspose.setOnClickListener {
            this.popup_transpose_dialog()
        }

        this.findViewById<View>(R.id.btnAddChannel).setOnClickListener {
            opus_manager.new_channel()
        }

        this.findViewById<View>(R.id.btnExportProject).setOnClickListener {
            this.export_midi()
        }

        this.findViewById<View>(R.id.btnSaveProject).setOnClickListener {
            this.save_current_project()
        }
        this.findViewById<View>(R.id.btnSaveProject).setOnLongClickListener {
            this.export_current_project()
            true
        }

        val btnDeleteProject = this.findViewById<View>(R.id.btnDeleteProject)
        val btnCopyProject = this.findViewById<View>(R.id.btnCopyProject)
        if (opus_manager.path != null && File(opus_manager.path!!).isFile) {
            btnDeleteProject.visibility = View.VISIBLE
            btnCopyProject.visibility = View.VISIBLE
        } else {
            btnDeleteProject.visibility = View.GONE
            btnCopyProject.visibility = View.GONE
        }
        btnDeleteProject.setOnClickListener {
            this.delete_project_dialog()
        }
        btnCopyProject.setOnClickListener {
            this.copy_project()
            this.closeDrawer()
        }
    }

    private fun change_name_dialog() {
        val main_fragment = this.getActiveFragment()

        val viewInflated: View = LayoutInflater.from(main_fragment!!.context)
            .inflate(
                R.layout.text_name_change,
                main_fragment.view as ViewGroup,
                false
            )

        val input: EditText = viewInflated.findViewById(R.id.etProjectName)
        input.setText(this.get_opus_manager().project_name)

        val opus_manager = this.get_opus_manager()
        AlertDialog.Builder(main_fragment.context, R.style.AlertDialog).apply {
            setTitle("Change Project Name")
            setView(viewInflated)
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                opus_manager.set_project_name(input.text.toString())
                dialog.dismiss()
            }
            setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            show()
        }
    }

    fun scroll_to_beat(beat: Int) {
        val fragment = this.getActiveFragment()
        if (fragment is EditorFragment) {
            fragment.scroll_to_beat(beat)
        }
    }

    fun save_current_project() {
        this.project_manager.save(this.opus_manager)
        this.feedback_msg("Project Saved")
        this.update_menu_options()
    }

    fun export_current_project() {
        var name = opus_manager.path
        if (name != null) {
            name = name.substring(name.lastIndexOf("/") + 1)
            name = name.substring(0, name.lastIndexOf("."))
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/midi"
            putExtra(Intent.EXTRA_TITLE, "$name.json")
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, "")
        }

        this.export_project_intent_launcher.launch(intent)

    }

    fun get_opus_manager(): OpusManager {
        return this.opus_manager
    }

    fun update_menu_options() {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        when (navHost?.childFragmentManager?.fragments?.get(0)) {
            is EditorFragment -> {
                this.optionsMenu.findItem(R.id.itmLoadProject).isVisible = this.has_projects_saved()
                this.optionsMenu.findItem(R.id.itmUndo).isVisible = true
                this.optionsMenu.findItem(R.id.itmNewProject).isVisible = true
                this.optionsMenu.findItem(R.id.itmPlay).isVisible = true
                this.optionsMenu.findItem(R.id.itmImportMidi).isVisible = true
                this.optionsMenu.findItem(R.id.itmImportProject).isVisible = true

            }
            else -> {
                this.optionsMenu.findItem(R.id.itmLoadProject).isVisible = false
                this.optionsMenu.findItem(R.id.itmUndo).isVisible = false
                this.optionsMenu.findItem(R.id.itmNewProject).isVisible = false
                this.optionsMenu.findItem(R.id.itmPlay).isVisible = false
                this.optionsMenu.findItem(R.id.itmImportMidi).isVisible = false
                this.optionsMenu.findItem(R.id.itmImportProject).isVisible = false
            }
        }
    }

    fun update_title_text() {
        this.set_title_text(this.get_opus_manager().project_name)
    }

    fun set_title_text(new_text: String) {
        this.binding.appBarMain.toolbar.title = new_text
    }

    fun play_event(channel: Int, event_value: Int) {
        val midi_channel = this.opus_manager.channels[channel].midi_channel
        val note = if (this.opus_manager.is_percussion(channel)) {
            event_value + 27
        } else {
            event_value + 21 + this.opus_manager.transpose
        }

        this@MainActivity.runOnUiThread {
            this.midi_input_device.sendEvent(NoteOn(midi_channel, note, 64))
            Thread.sleep(250)
            this.midi_input_device.sendEvent(NoteOff(midi_channel, note, 64))
        }
    }

    private fun play_midi(midi: MIDI) {
        this.midi_player.play_midi(midi)
    }

    private fun export_midi() {
        val opus_manager = this.get_opus_manager()

        var name = opus_manager.path
        if (name != null) {
            name = name.substring(name.lastIndexOf("/") + 1)
            name = name.substring(0, name.lastIndexOf("."))
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/midi"
            putExtra(Intent.EXTRA_TITLE, "$name.mid")
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, "")
        }

        this.export_midi_intent_launcher.launch(intent)
    }

    private fun delete_project_dialog() {
        val main_fragment = this.getActiveFragment()

        val title = this.get_opus_manager().project_name

        val that = this
        AlertDialog.Builder(main_fragment!!.context, R.style.AlertDialog).apply {
            setTitle("Really delete $title?")

            setPositiveButton(android.R.string.ok) { dialog, _ ->
                that.delete_project()
                dialog.dismiss()
                that.closeDrawer()
            }
            setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            show()
        }
    }

    private fun delete_project() {
        this.project_manager.delete(this.opus_manager)

        val fragment = this.getActiveFragment()
        fragment?.setFragmentResult("NEW", bundleOf())
        this.navTo("main")

        this.feedback_msg("Deleted \"$title\"")
    }

    private fun copy_project() {
        this.project_manager.copy(this.opus_manager)
        this.feedback_msg("Now working on copy")
    }

    private fun closeDrawer() {
        findViewById<DrawerLayout>(R.id.drawer_layout).closeDrawers()
    }

    fun navTo(fragmentName: String) {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        val fragment = navHost?.childFragmentManager?.fragments?.get(0)
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        when (fragment) {
            is LoadFragment -> {
                when (fragmentName) {
                    "main" -> {
                        if (navController.graph.startDestinationId != R.id.EditorFragment) {
                            this.reset_start_destination()
                        } else {
                            navController.navigate(R.id.action_LoadFragment_to_EditorFragment)
                        }
                    }
                    else -> {}
                }

            }
            is EditorFragment -> {
                this.in_play_back = false
                when (fragmentName) {
                    "load" -> {
                        navController.navigate(R.id.action_EditorFragment_to_LoadFragment)
                    }
                    else -> {}
                }
            }
            is LandingPageFragment -> {
                when (fragmentName) {
                    "main" -> {
                        this.reset_start_destination()
                    }
                    "load" -> {
                        navController.navigate(R.id.action_FrontFragment_to_LoadFragment)
                    }
                    "license" -> {
                        navController.navigate(R.id.action_FrontFragment_to_LicenseFragment)
                    }
                    else -> {}
                }
            }
            else -> {}
        }
    }

    fun getActiveFragment(): Fragment? {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        return navHost?.childFragmentManager?.fragments?.get(0)
    }

    fun feedback_msg(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // Can't use the general popup_number_dialog. We want a picker using radix-N
    private fun popup_transpose_dialog() {
        val viewInflated: View = LayoutInflater.from(this)
            .inflate(
                R.layout.dialog_split,
                window.decorView.rootView as ViewGroup,
                false
            )

        val npOnes = viewInflated.findViewById<NumberPicker>(R.id.npOnes)
        npOnes.minValue = 0
        npOnes.maxValue = 11
        npOnes.displayedValues = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B")
        npOnes.value = this.get_opus_manager().transpose

        val npTens = viewInflated.findViewById<NumberPicker>(R.id.npTens)
        npTens.visibility = View.GONE
        val npHundreds = viewInflated.findViewById<NumberPicker>(R.id.npHundreds)
        npHundreds.visibility = View.GONE


        AlertDialog.Builder(this, R.style.AlertDialog)
            .setTitle("Transpose")
            .setView(viewInflated)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = npOnes.value
                this.set_transpose(value)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    internal fun popup_number_dialog(title: String, min_value: Int, max_value: Int, default: Int? = null, callback: (value: Int) -> Unit) {
        val coerced_default_value = default ?: (this.number_selector_defaults[title] ?: min_value)

        val viewInflated: View = LayoutInflater.from(this)
            .inflate(
                R.layout.dialog_split,
                window.decorView.rootView as ViewGroup,
                false
            )

        val ones_min = if (min_value > 9 || max_value > 9) {
            0
        } else {
            min_value
        }

        val ones_max = if (max_value > 9) {
            9
        } else {
            max_value % 10
        }

        val npOnes = viewInflated.findViewById<NumberPicker>(R.id.npOnes)
        npOnes.minValue = ones_min
        npOnes.maxValue = ones_max

        val tens_min = if (min_value / 10 > 9 || max_value / 10 > 9) {
            0
        } else {
            (min_value / 10) % 10
        }

        val tens_max = if (max_value / 10 > 9) {
            9
        } else {
            (max_value / 10)
        }

        val npTens = viewInflated.findViewById<NumberPicker>(R.id.npTens)
        npTens.minValue = tens_min
        npTens.maxValue = tens_max

        val hundreds_min = if (min_value / 100 > 9 || max_value / 100 > 9) {
            0
        } else {
            (min_value / 100) % 10
        }

        val hundreds_max = if (max_value / 100 > 9) {
            9
        } else {
            (max_value / 100)
        }

        val npHundreds = viewInflated.findViewById<NumberPicker>(R.id.npHundreds)
        npHundreds.maxValue = hundreds_max
        npHundreds.minValue = hundreds_min

        npHundreds.value = (coerced_default_value / 100) % 10
        npTens.value = (coerced_default_value / 10) % 10
        npOnes.value = coerced_default_value % 10

        if (hundreds_max == 0) {
            npHundreds.visibility = View.GONE
            if (tens_max == 0) {
                npTens.visibility = View.GONE
            }
        }


        AlertDialog.Builder(this, R.style.AlertDialog)
            .setTitle(title)
            .setView(viewInflated)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = (npHundreds.value * 100) + (npTens.value * 10) + npOnes.value
                this.number_selector_defaults[title] = value
                callback(value)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
            }
            .show()
    }

    private fun set_tempo(value: Int) {
        val opus_manager = this.get_opus_manager()
        opus_manager.set_tempo(value.toFloat())
    }

    private fun set_transpose(value: Int) {
        val opus_manager = this.get_opus_manager()
        opus_manager.set_transpose(value)
    }

    fun loading_reticle() {
        if (this.progressBar == null) {
            this.progressBar =
                ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleLarge)
        }
        val params: RelativeLayout.LayoutParams = RelativeLayout.LayoutParams(50, 50)
        params.addRule(RelativeLayout.CENTER_IN_PARENT)
        var parent = this.progressBar!!.parent
        if (parent != null) {
            (parent as ViewGroup).removeView(this.progressBar)
        }
        this.binding.root.addView(this.progressBar, params)
    }

    fun cancel_reticle() {
        val progressBar = this.progressBar ?: return
        if (progressBar.parent != null) {
            (progressBar.parent as ViewGroup).removeView(progressBar)
        }
    }

    fun lockDrawer() {
        this.binding.root.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    fun unlockDrawer() {
        this.binding.root.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    // Change the navcontroller graph so the user can't return the the main menu screen
    private fun reset_start_destination() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        if (navController.graph.startDestinationId != R.id.EditorFragment) {
            val new_graph = navController.navInflater.inflate(R.navigation.nav_graph_b)
            navController.graph = new_graph

            this.appBarConfiguration = AppBarConfiguration(navController.graph)
            setupActionBarWithNavController(navController, this.appBarConfiguration)
        }
    }

    fun import_project(path: String) {
        this.applicationContext.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use {
            val bytes = FileInputStream(it.fileDescriptor).readBytes()

            this.opus_manager.load(bytes)
            this.opus_manager.path = this.project_manager.get_new_path()
        }
    }

    fun import_midi(path: String) {
        this.applicationContext.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use {
            val bytes = FileInputStream(it.fileDescriptor).readBytes()
            val midi = MIDI.from_bytes(bytes)
            var filename = java.net.URLDecoder.decode(path, "utf-8")
            filename = filename.substring(filename.lastIndexOf("/") + 1)
            filename = filename.substring(0, filename.lastIndexOf("."))

            val new_path = this.project_manager.get_new_path()
            this.opus_manager.import_midi(midi, new_path, filename)

        }
        this.cancel_reticle()
    }

    fun get_timestring_at_beat(beat: Int): String {
        val opus_manager = this.get_opus_manager()
        val tempo = opus_manager.tempo
        val milliseconds_per_beat = 60000F / tempo
        var milliseconds = (beat * milliseconds_per_beat).toInt()
        var seconds = milliseconds / 1000
        val minutes = seconds / 60
        seconds %= 60
        milliseconds %= 1000
        val centiseconds = milliseconds / 10
        val minute_string = "${minutes.toString().padStart(2,'0')}"
        val second_string = "${seconds.toString().padStart(2,'0')}"
        val centi_string = "${centiseconds.toString().padStart(2, '0')}"
        return "$minute_string:$second_string.$centi_string"
    }

    fun playback_dialog() {
        val viewInflated: View = LayoutInflater.from(this)
            .inflate(
                R.layout.playback_popup,
                window.decorView.rootView as ViewGroup,
                false
            )
        val opus_manager = this.get_opus_manager()
        val working_beat = opus_manager.cursor.beat
        var playing = false
        val sbPlaybackPosition = viewInflated.findViewById<SeekBar>(R.id.sbPlaybackPosition)
        sbPlaybackPosition.max = opus_manager.opus_beat_count - 1
        sbPlaybackPosition.progress = working_beat
        val tvPlaybackPosition = viewInflated.findViewById<TextView>(R.id.tvPlaybackPosition)
        tvPlaybackPosition.text = working_beat.toString()
        val ibPlayPause = viewInflated.findViewById<ImageView>(R.id.ibPlayPause)
        val btnJumpTo = viewInflated.findViewById<View>(R.id.btnJumpTo)
        val tvPlaybackTime = viewInflated.findViewById<TextView>(R.id.tvPlaybackTime)
        tvPlaybackTime.text = this.get_timestring_at_beat(working_beat)


        val midi_scroller = MIDIScroller(this, sbPlaybackPosition)
        this.midi_controller.registerVirtualDevice(midi_scroller)

        fun start_playback(x: Int) {
            playing = true
            ibPlayPause.setImageResource(R.drawable.ic_baseline_pause_24)
            thread {
                this.play_midi(opus_manager.get_midi(x))
                ibPlayPause.setImageResource(R.drawable.ic_baseline_play_arrow_24)
                playing = false
            }
        }

        fun pause_playback() {
            this.midi_input_device.sendEvent(MIDIStop())
        }


        ibPlayPause.setOnClickListener {
            if (playing) {
                pause_playback()
            } else {
                start_playback(sbPlaybackPosition.progress)
            }
        }

        val that = this
        sbPlaybackPosition.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            var was_playing = false
            var is_stopping = false
            var in_grace_period = false
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                tvPlaybackPosition.text = p1.toString()
                tvPlaybackTime.text = that.get_timestring_at_beat(p1)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                this.was_playing = playing
                this.is_stopping = true
                pause_playback()
                this.is_stopping = false
            }
            override fun onStopTrackingTouch(seekbar: SeekBar?) {
                if (!this.in_grace_period && this.was_playing && seekbar != null && !playing) {
                    // 'grace' period prevents multi-clicking from calling this multiple times
                    this.in_grace_period = true
                    // Kludge. need to give the midi play enough time to stop
                    Thread.sleep(500)

                    start_playback(seekbar.progress)
                    this.in_grace_period = false
                }
            }
        })

        this.midi_playback_device.precache_midi(this.get_opus_manager().get_midi())

        val dialog = AlertDialog.Builder(this, R.style.AlertDialog)
            .setView(viewInflated)
            .setOnCancelListener { _ ->
                this.midi_input_device.sendEvent(MIDIStop())
                this.midi_controller.unregisterVirtualDevice(midi_scroller)
                this.midi_playback_device.clear_sample_cache()
            }
            .show()

        btnJumpTo.setOnClickListener {
            pause_playback()
            this.scroll_to_beat(sbPlaybackPosition.progress)
            dialog.dismiss()
        }
    }

    fun has_projects_saved(): Boolean {
        return this.project_manager.has_projects_saved()
    }
}