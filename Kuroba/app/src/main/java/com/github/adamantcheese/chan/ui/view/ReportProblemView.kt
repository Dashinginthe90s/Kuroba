package com.github.adamantcheese.chan.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.manager.ReportManager
import com.github.adamantcheese.chan.ui.controller.LogsController
import com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.Logger
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.android.schedulers.AndroidSchedulers
import javax.inject.Inject

class ReportProblemView(context: Context) : FrameLayout(context) {

    @Inject
    lateinit var reportManager: ReportManager

    private var callbacks: ReportProblemControllerCallbacks? = null

    private val reportActivityProblemTitle: TextInputEditText
    private val reportActivityProblemDescription: TextInputEditText
    private val reportActivityAttachLogsButton: CheckBox
    private val reportActivityLogsText: TextView
    private val reportActivitySendReport: Button

    init {
        inject(this)

        inflate(context, R.layout.layout_report, this).apply {
            reportActivityProblemTitle = findViewById(R.id.report_controller_problem_title)
            reportActivityProblemDescription = findViewById(R.id.report_controller_problem_description)
            reportActivityAttachLogsButton = findViewById(R.id.report_controller_attach_logs_button)
            reportActivityLogsText = findViewById(R.id.report_controller_logs_text)
            reportActivitySendReport = findViewById(R.id.report_controller_send_report)
        }
    }

    fun onReady(controllerCallbacks: ReportProblemControllerCallbacks) {
        val logs = LogsController.loadLogs()
        if (logs != null) {
            reportActivityLogsText.text = logs
        }

        reportActivitySendReport.setOnClickListener { onSendReportClick() }

        this.callbacks = controllerCallbacks
    }

    @SuppressLint("CheckResult")
    private fun onSendReportClick() {
        if (callbacks == null) {
            return
        }

        val title = reportActivityProblemTitle.text?.toString() ?: ""
        val description = reportActivityProblemDescription.text?.toString() ?: ""
        val logs = reportActivityLogsText.text?.toString() ?: ""

        if (title.isEmpty()) {
            reportActivityProblemTitle.error = getString(R.string.report_controller_title_cannot_be_empty_error)
            return
        }

        if (
                description.isEmpty()
                && !(reportActivityAttachLogsButton.isChecked && logs.isNotEmpty())
        ) {
            reportActivityProblemDescription.error = getString(R.string.report_controller_description_cannot_be_empty_error)
            return
        }

        if (reportActivityAttachLogsButton.isChecked && logs.isEmpty()) {
            reportActivityLogsText.error = getString(R.string.report_controller_logs_are_empty_error)
            return
        }

        val logsParam = if (!reportActivityAttachLogsButton.isChecked) {
            null
        } else {
            logs
        }

        reportManager.sendReport(title, description, logsParam)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    if (result is ReportManager.Ok) {
                        showToast(context, R.string.report_controller_report_sent)
                    }
                }, { error ->
                    Logger.e(TAG, "Send report error", error)

                    val errorMessage = error.message ?: "No error message"
                    val formattedMessage = getString(
                            R.string.report_controller_error_while_trying_to_send_report,
                            errorMessage
                    )

                    showToast(context, formattedMessage)
                })

        callbacks?.onFinished()
        callbacks = null
    }

    interface ReportProblemControllerCallbacks {
        fun onFinished()
    }

    companion object {
        private const val TAG = "ReportProblemLayout"
    }
}