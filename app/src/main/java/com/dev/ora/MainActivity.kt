/*
 *
 * ▖  ▖             ▝
 * ▝▖▞  ▄▖  ▄▖  ▄▖ ▗▄  ▗▗▖  ▄▖
 *  ▜▘ ▝ ▐ ▐ ▝ ▐ ▝  ▐  ▐▘▐ ▐▘▐
 *  ▐  ▗▀▜  ▀▚  ▀▚  ▐  ▐ ▐ ▐▀▀
 *  ▐  ▝▄▜ ▝▄▞ ▝▄▞ ▗▟▄ ▐ ▐ ▝▙▞
 *
 *
 * MainActivity.kt - The Heart of Ora 💖
 * Where all the magic happens! From countdowns to notifications,
 * this is Mission Control for your digital life.
 *
 * Built with passion and countless cups of coffee ☕
 * by Yassine - "Time flies, but memories last forever"
 */

package com.dev.ora

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.dev.ora.adapter.CountdownAdapter
import com.dev.ora.data.CountdownEvent
import com.dev.ora.data.PreferencesManager
import com.dev.ora.databinding.ActivityMainBinding
import com.dev.ora.databinding.DialogAddEventBinding
import com.dev.ora.databinding.DialogAddDurationEventBinding
import com.dev.ora.notification.CountdownNotificationService
import com.dev.ora.viewmodel.MainViewModel
import com.dev.ora.utils.SwipeToActionCallback
import com.dev.ora.utils.CountdownUtils
import com.dev.ora.utils.ThemeManager
import com.dev.ora.reminder.ReminderScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var countdownAdapter: CountdownAdapter
    private var isFabMenuOpen = false
    private var allEventsList: List<CountdownEvent> = emptyList()
    private var currentSearchQuery: String = ""
    private var notificationSwitchReference: com.google.android.material.materialswitch.MaterialSwitch? = null
    private lateinit var reminderScheduler: ReminderScheduler
    private var isFirstLoad = true

    // File picker launchers for import/export
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportEventsToFile(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importEventsFromFile(it) }
    }

    private var pendingNotificationsEnabled: Boolean = false
    private var applyPendingNotificationsChange: Boolean = false
    private var isWelcomePermissionRequest: Boolean = false

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Update the temporary UI state
        pendingNotificationsEnabled = isGranted
        notificationSwitchReference?.isChecked = isGranted

        // Apply based on context
        when {
            applyPendingNotificationsChange -> {
                applyPendingNotificationsChange = false
                applyNotificationPreference(isGranted)
            }
            isWelcomePermissionRequest -> {
                isWelcomePermissionRequest = false
                applyNotificationPreference(isGranted)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize notification channels
        com.dev.ora.notification.NotificationHelper.createNotificationChannels(this)
        reminderScheduler = ReminderScheduler(this)

        setupWindowInsets()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        setupSearchBar()
        checkFirstLaunch()
    }

    private fun checkFirstLaunch() {
        val preferencesManager = PreferencesManager(this)
        if (preferencesManager.isFirstLaunch()) {
            lifecycleScope.launch {
                kotlinx.coroutines.delay(500)
                showWelcomeDialog()
            }
        }
    }

    private fun showWelcomeDialog() {
        val dialogBinding = com.dev.ora.databinding.DialogWelcomeBinding.inflate(layoutInflater)
        val preferencesManager = PreferencesManager(this)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.welcomeAnimation.apply {
            visibility = View.GONE
        }

        val welcomeTextView = dialogBinding.welcomeTitle
        val fullText = "Welcome to ORA !"
        val spannable = android.text.SpannableString(fullText)
        val start = fullText.indexOf("ORA")
        val end = start + "ORA".length
        val orbitron = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.orbitron)
        if (orbitron != null) {
            spannable.setSpan(CustomTypefaceSpan("", orbitron), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        welcomeTextView.text = spannable

        dialogBinding.enableNotificationsButton.setOnClickListener {
            preferencesManager.setFirstLaunchComplete()
            dialog.dismiss()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isWelcomePermissionRequest = true
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                applyNotificationPreference(true)
            }
        }

        dialogBinding.skipButton.setOnClickListener {
            preferencesManager.setFirstLaunchComplete()
            preferencesManager.setAskedForNotifications(true)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyNotificationPreference(isGranted: Boolean) {
        val preferencesManager = PreferencesManager(this)
        preferencesManager.saveNotificationsEnabled(isGranted)

        if (isGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    Toast.makeText(this, "Please grant permission for reliable reminders", Toast.LENGTH_LONG).show()
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
                        startActivity(it)
                    }
                }
            }
            lifecycleScope.launch {
                try {
                    val events = viewModel.getAllEventsList()
                    events.forEach { event ->
                        if (event.hasReminders) reminderScheduler.rescheduleReminders(event)
                    }
                    CountdownNotificationService.startOrUpdateService(this@MainActivity)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Notifications enabled", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {}
            }
        } else {
            lifecycleScope.launch {
                try {
                    val events = viewModel.getAllEventsList()
                    events.forEach { event ->
                        reminderScheduler.cancelReminders(event.id)
                    }
                    CountdownNotificationService.stopService(this@MainActivity)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Notifications disabled", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun initializeNotifications() {
        val preferencesManager = PreferencesManager(this)
        if (!preferencesManager.getNotificationsEnabled()) {
            lifecycleScope.launch {
                try {
                    CountdownNotificationService.stopService(this@MainActivity)
                    val events = viewModel.getAllEventsList()
                    events.forEach { event ->
                        if (event.hasReminders) reminderScheduler.cancelReminders(event.id)
                    }
                } catch (_: Exception) {}
            }
            return
        }
        lifecycleScope.launch {
            try {
                if (preferencesManager.getAutoDeleteExpired()) {
                    performAutoDelete()
                }
                val events = viewModel.getAllEventsList()
                events.forEach { event ->
                    if (event.hasReminders) {
                        reminderScheduler.rescheduleReminders(event)
                    }
                }
                CountdownNotificationService.startOrUpdateService(this@MainActivity)
            } catch (_: Exception) {}
        }
    }

    private suspend fun performAutoDelete() {
        try {
            val expiredEvents = viewModel.getExpiredEventsList()
            val eventsToDelete = expiredEvents.filter { !it.isPinned }
            eventsToDelete.forEach { event ->
                if (event.hasReminders) {
                    reminderScheduler.cancelReminders(event.id)
                }
                viewModel.deleteEvent(event)
            }
        } catch (_: Exception) {}
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val searchInput = findViewById<EditText>(R.id.searchInput)
            if (searchInput != null && searchInput.isFocused) {
                val outRect = android.graphics.Rect()
                searchInput.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    searchInput.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun setupKeyboardHiding(rootView: View, vararg inputViews: View) {
        rootView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val currentFocus = (v.context as? AppCompatActivity)?.currentFocus
                if (currentFocus != null && inputViews.contains(currentFocus)) {
                    val rect = android.graphics.Rect()
                    currentFocus.getGlobalVisibleRect(rect)
                    if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        hideKeyboard(currentFocus)
                    }
                }
            }
            false
        }
    }

    private fun setupSearchBar() {
        val searchInput = findViewById<EditText>(R.id.searchInput)
        val settingsButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.settingsButton)
        var searchJob: kotlinx.coroutines.Job? = null
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString() ?: ""
                updateSearchIcon(settingsButton, query.isNotEmpty())
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    filterEvents(query)
                }
            }
        })
        settingsButton?.setOnClickListener {
            if (searchInput?.text?.isNotEmpty() == true) {
                searchInput.text?.clear()
                searchInput.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
            } else {
                showSettingsDialog()
            }
        }
        searchInput?.setOnEditorActionListener { _, _, _ ->
            searchInput.clearFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
            true
        }
    }

    private fun updateSearchIcon(button: com.google.android.material.button.MaterialButton?, showClearIcon: Boolean) {
        button?.apply {
            if (showClearIcon) {
                setIconResource(R.drawable.ic_close_24)
                contentDescription = "Clear search"
            } else {
                setIconResource(R.drawable.ic_settings_24)
                contentDescription = "Settings"
            }
        }
    }

    private fun filterEvents(query: String) {
        currentSearchQuery = query
        if (query.isEmpty()) {
            countdownAdapter.submitList(allEventsList)
            binding.noResultsLayout.visibility = View.GONE
            updateEmptyState(allEventsList.isEmpty())
        } else {
            val filteredEvents = allEventsList.filter { event ->
                event.title.contains(query, ignoreCase = true) ||
                        event.description.contains(query, ignoreCase = true)
            }
            countdownAdapter.submitList(filteredEvents)
            if (filteredEvents.isEmpty()) {
                binding.noResultsLayout.visibility = View.VISIBLE
                binding.emptyStateLayout.visibility = View.GONE
                binding.countdownRecyclerView.visibility = View.GONE
            } else {
                binding.noResultsLayout.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.GONE
                binding.countdownRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        countdownAdapter = CountdownAdapter(
            onItemClick = { event ->
                val intent = Intent(this, EventDetailActivity::class.java).apply {
                    putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)
                    putExtra(EventDetailActivity.EXTRA_EVENT_TITLE, event.title)
                    putExtra(EventDetailActivity.EXTRA_EVENT_DESCRIPTION, event.description)
                    putExtra(EventDetailActivity.EXTRA_EVENT_TARGET_DATE, event.targetDate.time)
                    putExtra(EventDetailActivity.EXTRA_EVENT_CREATED_DATE, event.createdAt.time)
                    putExtra(EventDetailActivity.EXTRA_EVENT_COLOR, event.color)
                    putExtra(EventDetailActivity.EXTRA_EVENT_IS_PINNED, event.isPinned)
                    putExtra(EventDetailActivity.EXTRA_EVENT_HAS_REMINDERS, event.hasReminders)
                }
                startActivity(intent)
            },
            onMenuClick = { event, view ->
                showEventMenu(event, view)
            }
        )
        binding.countdownRecyclerView.apply {
            adapter = countdownAdapter
            layoutManager = GridLayoutManager(this@MainActivity, 1)
            setHasFixedSize(true)
            layoutAnimation = android.view.animation.AnimationUtils.loadLayoutAnimation(
                this@MainActivity, R.anim.layout_animation_popup
            )
        }
        val swipeCallback = SwipeToActionCallback(
            context = this,
            onEdit = { position ->
                countdownAdapter.notifyItemChanged(position)
                val event = countdownAdapter.currentList[position]
                showEditEventDialogInline(event)
            },
            onDelete = { position ->
                val event = countdownAdapter.currentList[position]
                showDeleteConfirmationInline(event, position)
            }
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.countdownRecyclerView)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.allEvents.collectLatest { events ->
                countdownAdapter.submitList(events)
                updateEmptyState(events.isEmpty())
                allEventsList = events
                if (currentSearchQuery.isNotEmpty()) {
                    filterEvents(currentSearchQuery)
                }
                if (isFirstLoad) {
                    if (events.isNotEmpty()) {
                        binding.countdownRecyclerView.scheduleLayoutAnimation()
                    }
                    isFirstLoad = false
                    kotlinx.coroutines.delay(200)
                    initializeNotifications()
                }
            }
        }
        viewModel.errorMessage.observe(this) { errorMessage ->
            errorMessage?.let {
                viewModel.clearErrorMessage()
            }
        }
        viewModel.expiredEventsCount.observe(this) { expiredCount ->
            expiredCount?.let { count ->
                if (count > 0) {
                    viewModel.clearExpiredEventsCount()
                }
            }
        }
        viewModel.pinActionResult.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearPinActionResult()
            }
        }
    }

    private fun setupClickListeners() {
        val aboutButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.aboutButton)
        aboutButton?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
            startActivity(intent)
        }
        binding.fabAddEvent.setOnClickListener { toggleFabMenu() }
        binding.fabDateTime.setOnClickListener {
            closeFabMenu()
            showAddEventDialog()
        }
        binding.fabDuration.setOnClickListener {
            closeFabMenu()
            showAddDurationEventDialog()
        }
        binding.fabMenuBackdrop.setOnClickListener { closeFabMenu() }
    }

    private fun toggleFabMenu() {
        if (isFabMenuOpen) closeFabMenu() else openFabMenu()
    }

    private fun openFabMenu() {
        isFabMenuOpen = true
        binding.fabMenuBackdrop.visibility = View.VISIBLE
        binding.fabMenuBackdrop.alpha = 0f
        binding.fabMenuBackdrop.animate().alpha(1f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
        binding.fabAddEvent.animate().rotation(45f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
        binding.fabDuration.visibility = View.VISIBLE
        binding.fabDurationLabel.visibility = View.VISIBLE
        binding.fabDuration.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).setStartDelay(50).setInterpolator(AccelerateDecelerateInterpolator()).start()
        binding.fabDurationLabel.animate().alpha(1f).setDuration(150).setStartDelay(50).setInterpolator(AccelerateDecelerateInterpolator()).start()
        binding.fabDateTime.visibility = View.VISIBLE
        binding.fabDateTimeLabel.visibility = View.VISIBLE
        binding.fabDateTime.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).setStartDelay(100).setInterpolator(AccelerateDecelerateInterpolator()).start()
        binding.fabDateTimeLabel.animate().alpha(1f).setDuration(150).setStartDelay(100).setInterpolator(AccelerateDecelerateInterpolator()).start()
    }

    private fun closeFabMenu() {
        if (!isFabMenuOpen) return
        isFabMenuOpen = false
        binding.fabMenuBackdrop.animate().alpha(0f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction { binding.fabMenuBackdrop.visibility = View.GONE }.start()
        binding.fabAddEvent.animate().rotation(0f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
        binding.fabDateTime.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(150).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction { binding.fabDateTime.visibility = View.GONE }.start()
        binding.fabDateTimeLabel.animate().alpha(0f).setDuration(150).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction { binding.fabDateTimeLabel.visibility = View.GONE }.start()
        binding.fabDuration.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(150).setStartDelay(50).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction { binding.fabDuration.visibility = View.GONE }.start()
        binding.fabDurationLabel.animate().alpha(0f).setDuration(150).setStartDelay(50).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction { binding.fabDurationLabel.visibility = View.GONE }.start()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.countdownRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    private fun showAddEventDialog() {
        val dialogBinding = DialogAddEventBinding.inflate(layoutInflater)
        var selectedDate: Calendar?
        var selectedColor = "#DC143C"

        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
        selectedDate = tomorrow
        updateDateTimeButtons(dialogBinding, selectedDate)

        setupColorSelection(dialogBinding) { color ->
            selectedColor = color
        }

        dialogBinding.selectDateButton.setOnClickListener {
            val currentDate = selectedDate ?: Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val newDate = selectedDate ?: Calendar.getInstance()
                    newDate.set(year, month, dayOfMonth)
                    selectedDate = newDate
                    updateDateTimeButtons(dialogBinding, selectedDate)
                },
                currentDate.get(Calendar.YEAR),
                currentDate.get(Calendar.MONTH),
                currentDate.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        dialogBinding.selectTimeButton.setOnClickListener {
            val currentTime = selectedDate ?: Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val newTime = selectedDate ?: Calendar.getInstance()
                    newTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    newTime.set(Calendar.MINUTE, minute)
                    newTime.set(Calendar.SECOND, 0)
                    selectedDate = newTime
                    updateDateTimeButtons(dialogBinding, selectedDate)
                },
                currentTime.get(Calendar.HOUR_OF_DAY),
                currentTime.get(Calendar.MINUTE),
                true
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        setupKeyboardHiding(dialogBinding.root, dialogBinding.titleInput, dialogBinding.descriptionInput)

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.createButton.setOnClickListener {
            val title = dialogBinding.titleInput.text.toString().trim()
            val description = dialogBinding.descriptionInput.text.toString().trim()

            if (selectedDate == null || selectedDate!!.before(Calendar.getInstance())) {
                Toast.makeText(this, "Please select a future date and time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val finalTitle = if (title.isEmpty()) {
                val existingTitles = countdownAdapter.currentList.map { it.title }
                CountdownUtils.generateDefaultEventName(selectedDate!!.time, existingTitles)
            } else {
                title
            }
            dialog.dismiss()

            lifecycleScope.launch {
                val newEvent = viewModel.addEventAndReturn(
                    finalTitle,
                    description,
                    selectedDate!!.time,
                    selectedColor
                )
                if (newEvent.hasReminders) {
                    reminderScheduler.scheduleReminders(newEvent)
                }
                CountdownNotificationService.startOrUpdateService(this@MainActivity)
                Toast.makeText(this@MainActivity, "Event created: $finalTitle", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun showEditEventDialog(event: CountdownEvent) {
        val dialogBinding = DialogAddEventBinding.inflate(layoutInflater)
        var selectedDate: Calendar? = Calendar.getInstance().apply { time = event.targetDate }
        var selectedColor = event.color

        dialogBinding.titleInput.setText(event.title)
        dialogBinding.descriptionInput.setText(event.description)
        dialogBinding.dialogTitle.text = "Edit Event"
        updateDateTimeButtons(dialogBinding, selectedDate)
        setupColorSelectionWithPreselect(dialogBinding, event.color) { color -> selectedColor = color }

        dialogBinding.selectDateButton.setOnClickListener {
            val currentDate = selectedDate ?: Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val newDate = selectedDate ?: Calendar.getInstance()
                newDate.set(year, month, dayOfMonth)
                selectedDate = newDate
                updateDateTimeButtons(dialogBinding, selectedDate)
            }, currentDate.get(Calendar.YEAR), currentDate.get(Calendar.MONTH), currentDate.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.selectTimeButton.setOnClickListener {
            val currentTime = selectedDate ?: Calendar.getInstance()
            TimePickerDialog(this, { _, hourOfDay, minute ->
                val newTime = selectedDate ?: Calendar.getInstance()
                newTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                newTime.set(Calendar.MINUTE, minute)
                newTime.set(Calendar.SECOND, 0)
                selectedDate = newTime
                updateDateTimeButtons(dialogBinding, selectedDate)
            }, currentTime.get(Calendar.HOUR_OF_DAY), currentTime.get(Calendar.MINUTE), true).show()
        }

        val dialog = MaterialAlertDialogBuilder(this).setView(dialogBinding.root).create()
        dialogBinding.createButton.text = "Save Changes"
        setupKeyboardHiding(dialogBinding.root, dialogBinding.titleInput, dialogBinding.descriptionInput)
        dialogBinding.cancelButton.setOnClickListener { dialog.dismiss() }

        dialogBinding.createButton.setOnClickListener {
            val title = dialogBinding.titleInput.text.toString().trim()
            val description = dialogBinding.descriptionInput.text.toString().trim()

            if (title.isEmpty()) {
                dialogBinding.titleInputLayout.error = "Please enter an event title"
                return@setOnClickListener
            }
            if (selectedDate == null || selectedDate!!.before(Calendar.getInstance())) {
                Toast.makeText(this, "Please select a future date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedEvent = event.copy(
                title = title,
                description = description,
                targetDate = selectedDate!!.time,
                color = selectedColor
            )

            viewModel.updateEvent(updatedEvent)
            if (updatedEvent.hasReminders) {
                reminderScheduler.rescheduleReminders(updatedEvent)
            } else {
                reminderScheduler.cancelReminders(updatedEvent.id)
            }
            CountdownNotificationService.startOrUpdateService(this@MainActivity)

            dialog.dismiss()
            Toast.makeText(this, "Event updated", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun showEditEventDialogInline(event: CountdownEvent) {
        showEditEventDialog(event)
    }

    private fun updateDateTimeButtons(dialogBinding: DialogAddEventBinding, selectedDate: Calendar?) {
        selectedDate?.let { date ->
            val dateFormat = java.text.SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val timeFormat = java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
            dialogBinding.selectDateButton.text = dateFormat.format(date.time)
            dialogBinding.selectTimeButton.text = timeFormat.format(date.time)
        }
    }

    private fun setupColorSelection(dialogBinding: DialogAddEventBinding, onColorSelected: (String) -> Unit) {
        val colors = listOf("#DC143C", "#007BFF", "#DFFF00", "#6A5ACD", "CUSTOM")
        val colorViews = listOf(
            dialogBinding.color1,
            dialogBinding.color2,
            dialogBinding.color3,
            dialogBinding.color4,
            dialogBinding.color5
        )
        colorViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                if (index == 4) {
                    showCustomColorPickerDialog { customColor ->
                        colorViews.forEach { it.isSelected = false }
                        updateCustomColorButton(view, customColor)
                        view.isSelected = true
                        onColorSelected(customColor)
                    }
                } else {
                    colorViews.forEach { it.isSelected = false }
                    view.isSelected = true
                    onColorSelected(colors[index])
                }
            }
        }
        colorViews.first().isSelected = true
    }

    private fun setupColorSelectionWithPreselect(dialogBinding: DialogAddEventBinding, preselectedColor: String, onColorSelected: (String) -> Unit) {
        val colors = listOf("#DC143C", "#007BFF", "#DFFF00", "#6A5ACD", "CUSTOM")
        val colorViews = listOf(
            dialogBinding.color1,
            dialogBinding.color2,
            dialogBinding.color3,
            dialogBinding.color4,
            dialogBinding.color5
        )
        colorViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                if (index == 4) {
                    showCustomColorPickerDialog { customColor ->
                        colorViews.forEach { it.isSelected = false }
                        updateCustomColorButton(view, customColor)
                        view.isSelected = true
                        onColorSelected(customColor)
                    }
                } else {
                    colorViews.forEach { it.isSelected = false }
                    view.isSelected = true
                    onColorSelected(colors[index])
                }
            }
        }
        val preselectedIndex = colors.indexOf(preselectedColor)
        if (preselectedIndex != -1) {
            colorViews[preselectedIndex].isSelected = true
        } else {
            updateCustomColorButton(colorViews[4], preselectedColor)
            colorViews[4].isSelected = true
        }
    }

    private fun showEventMenu(event: CountdownEvent, anchorView: View) {
        PopupMenu(this, anchorView).apply {
            inflate(R.menu.event_menu)
            menu.findItem(R.id.menu_pin)?.title = if (event.isPinned) "Unpin" else "Pin"
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_pin -> {
                        viewModel.togglePinStatus(event)
                        CountdownNotificationService.startOrUpdateService(this@MainActivity)
                        true
                    }
                    R.id.menu_edit -> {
                        showEditEventDialog(event)
                        true
                    }
                    R.id.menu_delete -> {
                        val position = countdownAdapter.currentList.indexOf(event)
                        if (position != -1) {
                            showDeleteConfirmation(event, position)
                        }
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showDeleteConfirmation(event: CountdownEvent, position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete \"${event.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                if (event.hasReminders) {
                    reminderScheduler.cancelReminders(event.id)
                }
                viewModel.deleteEvent(event)
                CountdownNotificationService.startOrUpdateService(this@MainActivity)
                Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                countdownAdapter.notifyItemChanged(position)
            }
            .show()
    }

    private fun showDeleteConfirmationInline(event: CountdownEvent, position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete \"${event.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                if (event.hasReminders) {
                    reminderScheduler.cancelReminders(event.id)
                }
                viewModel.deleteEvent(event)
                CountdownNotificationService.startOrUpdateService(this@MainActivity)
                Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                countdownAdapter.notifyItemChanged(position)
            }
            .setOnCancelListener {
                countdownAdapter.notifyItemChanged(position)
            }
            .show()
    }

    private fun showAddDurationEventDialog() {
        val dialogBinding = DialogAddDurationEventBinding.inflate(layoutInflater)
        var selectedColor = "#DC143C"
        setupDurationColorSelection(dialogBinding) { color -> selectedColor = color }
        setupQuickDurationChips(dialogBinding)
        val dialog = MaterialAlertDialogBuilder(this).setView(dialogBinding.root).create()
        setupKeyboardHiding(dialogBinding.root, dialogBinding.titleInput, dialogBinding.descriptionInput, dialogBinding.daysInput, dialogBinding.hoursInput, dialogBinding.minutesInput)
        dialogBinding.cancelButton.setOnClickListener { dialog.dismiss() }
        dialogBinding.createButton.setOnClickListener {
            val title = dialogBinding.titleInput.text.toString().trim()
            val description = dialogBinding.descriptionInput.text.toString().trim()
            val days = dialogBinding.daysInput.text.toString().toIntOrNull() ?: 0
            val hours = dialogBinding.hoursInput.text.toString().toIntOrNull() ?: 0
            val minutes = dialogBinding.minutesInput.text.toString().toIntOrNull() ?: 0
            if (days == 0 && hours == 0 && minutes == 0) {
                return@setOnClickListener
            }
            val targetDate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, days)
                add(Calendar.HOUR_OF_DAY, hours)
                add(Calendar.MINUTE, minutes)
            }
            val finalTitle = if (title.isEmpty()) CountdownUtils.generateDefaultEventName(targetDate.time, countdownAdapter.currentList.map { it.title }) else title
            dialog.dismiss()
            lifecycleScope.launch {
                val newEvent = viewModel.addEventAndReturn(finalTitle, description, targetDate.time, selectedColor)
                if (newEvent.hasReminders) {
                    reminderScheduler.scheduleReminders(newEvent)
                }
                CountdownNotificationService.startOrUpdateService(this@MainActivity)
                Toast.makeText(this@MainActivity, "Event created: $finalTitle", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun setupDurationColorSelection(dialogBinding: DialogAddDurationEventBinding, onColorSelected: (String) -> Unit) {
        val colors = listOf("#DC143C", "#007BFF", "#DFFF00", "#6A5ACD", "CUSTOM")
        val colorViews = listOf(dialogBinding.color1, dialogBinding.color2, dialogBinding.color3, dialogBinding.color4, dialogBinding.color5)
        colorViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                if (index == 4) {
                    showCustomColorPickerDialog { customColor ->
                        colorViews.forEach { it.isSelected = false }
                        updateCustomColorButton(view, customColor)
                        view.isSelected = true
                        onColorSelected(customColor)
                    }
                } else {
                    colorViews.forEach { it.isSelected = false }
                    view.isSelected = true
                    onColorSelected(colors[index])
                }
            }
        }
        colorViews.first().isSelected = true
    }

    private fun setupQuickDurationChips(dialogBinding: DialogAddDurationEventBinding) {
        val chips = listOf(dialogBinding.chip5min, dialogBinding.chip15min, dialogBinding.chip30min, dialogBinding.chip1hour, dialogBinding.chip1day)
        dialogBinding.chip5min.setOnClickListener { clearAllChips(chips); dialogBinding.minutesInput.setText("5");(it as com.google.android.material.chip.Chip).isChecked = true }
        dialogBinding.chip15min.setOnClickListener { clearAllChips(chips); dialogBinding.minutesInput.setText("15");(it as com.google.android.material.chip.Chip).isChecked = true }
        dialogBinding.chip30min.setOnClickListener { clearAllChips(chips); dialogBinding.minutesInput.setText("30");(it as com.google.android.material.chip.Chip).isChecked = true }
        dialogBinding.chip1hour.setOnClickListener { clearAllChips(chips); dialogBinding.hoursInput.setText("1");(it as com.google.android.material.chip.Chip).isChecked = true }
        dialogBinding.chip1day.setOnClickListener { clearAllChips(chips); dialogBinding.daysInput.setText("1");(it as com.google.android.material.chip.Chip).isChecked = true }
    }

    private fun clearAllChips(chips: List<com.google.android.material.chip.Chip>) {
        chips.forEach { it.isChecked = false }
    }

    private fun showCustomColorPickerDialog(onColorSelected: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val colorPickerView = dialogView.findViewById<com.flask.colorpicker.ColorPickerView>(R.id.colorPickerView)
        val colorPreview = dialogView.findViewById<View>(R.id.colorPreview)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        val selectButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectButton)
        var selectedColor = android.graphics.Color.RED
        colorPickerView.addOnColorChangedListener { color ->
            selectedColor = color
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
                setStroke(4, resources.getColor(android.R.color.darker_gray, theme))
            }
            colorPreview.background = drawable
        }
        val dialog = MaterialAlertDialogBuilder(this).setView(dialogView).create()
        cancelButton.setOnClickListener { dialog.dismiss() }
        selectButton.setOnClickListener {
            val hexColor = String.format("#%06X", 0xFFFFFF and selectedColor)
            onColorSelected(hexColor)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateCustomColorButton(view: View, customColor: String) {
        val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(createRainbowGradient(), createWhiteRing(), createColorCenter(customColor)))
        layerDrawable.setLayerInset(1, 8, 8, 8, 8)
        layerDrawable.setLayerInset(2, 12, 12, 12, 12)
        view.background = layerDrawable
    }

    private fun createRainbowGradient(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            colors = intArrayOf(android.graphics.Color.RED, android.graphics.Color.YELLOW, android.graphics.Color.GREEN, android.graphics.Color.CYAN, android.graphics.Color.BLUE, android.graphics.Color.MAGENTA, android.graphics.Color.RED)
            gradientType = android.graphics.drawable.GradientDrawable.SWEEP_GRADIENT
        }
    }

    private fun createWhiteRing(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(resources.getColor(android.R.color.white, theme))
        }
    }

    private fun createColorCenter(hexColor: String): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.parseColor(hexColor))
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val themeRadioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.themeRadioGroup)
        val radioLight = dialogView.findViewById<RadioButton>(R.id.radioLight)
        val radioDark = dialogView.findViewById<RadioButton>(R.id.radioDark)
        val radioSystem = dialogView.findViewById<RadioButton>(R.id.radioSystem)
        val notificationsSwitch = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.notificationsSwitch)
        val multipleEventsSwitch = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.multipleEventsSwitch)
        val maxEventsContainer = dialogView.findViewById<LinearLayout>(R.id.maxEventsContainer)
        val maxEventsSlider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.maxEventsSlider)
        val maxEventsValue = dialogView.findViewById<TextView>(R.id.maxEventsValue)
        val autoDeleteSwitch = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.autoDeleteSwitch)
        val exportButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.exportButton)
        val importButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.importButton)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        val applyButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.applyButton)
        val preferencesManager = PreferencesManager(this)
        val currentTheme = ThemeManager.getCurrentTheme(this)
        when (currentTheme) {
            PreferencesManager.THEME_LIGHT -> radioLight.isChecked = true
            PreferencesManager.THEME_DARK -> radioDark.isChecked = true
            PreferencesManager.THEME_SYSTEM -> radioSystem.isChecked = true
        }
        val currentNotificationsEnabled = preferencesManager.getNotificationsEnabled()
        notificationsSwitch.isChecked = currentNotificationsEnabled
        val currentMultipleEventsEnabled = preferencesManager.getMultipleEventsNotification()
        multipleEventsSwitch.isChecked = currentMultipleEventsEnabled
        val currentMaxEvents = preferencesManager.getMaxEventsInNotification()
        maxEventsSlider.value = currentMaxEvents.toFloat()
        maxEventsValue.text = "$currentMaxEvents events"
        maxEventsContainer.visibility = if (currentMultipleEventsEnabled) View.VISIBLE else View.GONE
        val currentAutoDelete = preferencesManager.getAutoDeleteExpired()
        autoDeleteSwitch.isChecked = currentAutoDelete
        val dialog = MaterialAlertDialogBuilder(this).setView(dialogView).setCancelable(true).create()
        notificationSwitchReference = notificationsSwitch
        pendingNotificationsEnabled = currentNotificationsEnabled
        var selectedTheme = currentTheme
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedTheme = when (checkedId) {
                R.id.radioLight -> PreferencesManager.THEME_LIGHT
                R.id.radioDark -> PreferencesManager.THEME_DARK
                R.id.radioSystem -> PreferencesManager.THEME_SYSTEM
                else -> PreferencesManager.THEME_SYSTEM
            }
        }
        multipleEventsSwitch.setOnCheckedChangeListener { _, isChecked -> maxEventsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE }
        maxEventsSlider.addOnChangeListener { _, value, _ -> maxEventsValue.text = "${value.toInt()} events" }
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            pendingNotificationsEnabled = isChecked
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
        exportButton.setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            exportLauncher.launch("project_curiosity_events_$timestamp.json")
        }
        importButton.setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        cancelButton.setOnClickListener { dialog.dismiss() }
        applyButton.setOnClickListener {
            if (selectedTheme != currentTheme) {
                ThemeManager.applyTheme(this, selectedTheme)
            }
            val newMultipleEventsEnabled = multipleEventsSwitch.isChecked
            val newMaxEvents = maxEventsSlider.value.toInt()
            var notificationSettingsChanged = false
            if (newMultipleEventsEnabled != currentMultipleEventsEnabled) {
                preferencesManager.saveMultipleEventsNotification(newMultipleEventsEnabled)
                notificationSettingsChanged = true
            }
            if (newMaxEvents != currentMaxEvents) {
                preferencesManager.saveMaxEventsInNotification(newMaxEvents)
                notificationSettingsChanged = true
            }
            if (notificationSettingsChanged && preferencesManager.getNotificationsEnabled()) {
                CountdownNotificationService.startOrUpdateService(this@MainActivity)
            }
            val newNotificationsEnabled = pendingNotificationsEnabled
            if (newNotificationsEnabled != currentNotificationsEnabled) {
                if (newNotificationsEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        applyPendingNotificationsChange = true
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        applyNotificationPreference(true)
                    }
                } else {
                    applyNotificationPreference(false)
                }
            }
            val newAutoDelete = autoDeleteSwitch.isChecked
            if (newAutoDelete != currentAutoDelete) {
                preferencesManager.saveAutoDeleteExpired(newAutoDelete)
                if (newAutoDelete) {
                    lifecycleScope.launch {
                        performAutoDelete()
                        CountdownNotificationService.startOrUpdateService(this@MainActivity)
                    }
                }
            }
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.setOnDismissListener { notificationSwitchReference = null }
        dialog.show()
    }

    private fun exportEventsToFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val events = viewModel.getAllEventsList()
                if (events.isEmpty()) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "No events to export", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                val eventsJson = Gson().toJson(events)
                contentResolver.openOutputStream(uri)?.use { it.write(eventsJson.toByteArray()) }
                runOnUiThread { Toast.makeText(this@MainActivity, "Exported ${events.size} events", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Failed to export events", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun importEventsFromFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val eventsJson = inputStream?.bufferedReader().use { it?.readText() }
                if (eventsJson.isNullOrEmpty()) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Selected file is empty", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                val eventListType = object : TypeToken<List<CountdownEvent>>() {}.type
                val events: List<CountdownEvent> = Gson().fromJson(eventsJson, eventListType)
                if (events.isEmpty()) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "No events found in file", Toast.LENGTH_SHORT).show() }
                    return@launch
                }

                // === ✨ COMPILER ERROR FIXED ✨ ===
                // We now use the correct `addEventAndReturn` suspend function.
                events.forEach { event ->
                    viewModel.addEventAndReturn(event.title, event.description, event.targetDate, event.color)
                }

                // Wait a moment for all DB operations to complete before the final reschedule.
                kotlinx.coroutines.delay(500)

                val allEvents = viewModel.getAllEventsList()
                allEvents.forEach { event ->
                    if (event.hasReminders) {
                        reminderScheduler.rescheduleReminders(event)
                    }
                }
                CountdownNotificationService.startOrUpdateService(this@MainActivity)

                runOnUiThread { Toast.makeText(this@MainActivity, "Imported ${events.size} events", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Failed to import events", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}