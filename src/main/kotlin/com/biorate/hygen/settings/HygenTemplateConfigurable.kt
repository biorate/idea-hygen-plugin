package com.biorate.hygen.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Settings → Tools → Hygen Templates panel (design D2). An editable list of wildcard filename
 * patterns, one-click presets ([HygenPatterns.PRESETS]) and inline validation.
 *
 * [createComponent] renders the list; [isModified]/[apply]/[reset] operate on the live
 * [HygenTemplateSettings]. Invalid entries surface a visible error message and [apply] throws
 * [ConfigurationException] so the Settings dialog blocks the change (spec: "invalid pattern
 * rejected"). A valid [apply] stores the canonicalized list and broadcasts
 * [HygenTemplateSettingsChangeListener.TOPIC] so associations re-sync immediately.
 */
class HygenTemplateConfigurable : Configurable {

    private val settings = HygenTemplateSettings.getInstance()
    private val model = DefaultListModel<String>()
    private val list = JBList(model)
    private val errorLabel = JLabel(" ")

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        // Prevent the placeholder label from stealing layout height when no error is shown.
        errorLabel.isVisible = false
    }

    override fun getDisplayName(): String = "Hygen Templates"

    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val listPanel = JPanel(BorderLayout())
        listPanel.add(JBScrollPane(list), BorderLayout.CENTER)

        val editButtons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        editButtons.add(JButton("Add...").apply {
            addActionListener { addPattern() }
            setMnemonic('A')
        })
        editButtons.add(JButton("Edit...").apply {
            addActionListener { editSelected() }
            setMnemonic('E')
        })
        editButtons.add(JButton("Remove").apply {
            addActionListener { removeSelected() }
            setMnemonic('R')
        })
        editButtons.add(JButton("Move Up").apply {
            addActionListener { moveSelected(-1) }
        })
        editButtons.add(JButton("Move Down").apply {
            addActionListener { moveSelected(1) }
        })

        val presets = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        presets.add(JLabel("Presets:"))
        for (preset in HygenPatterns.PRESETS) {
            presets.add(JButton(preset).apply {
                toolTipText = "Replace the list with $preset"
                addActionListener { applyPreset(preset) }
            })
        }

        val controls = JPanel(BorderLayout(0, 4))
        controls.add(editButtons, BorderLayout.NORTH)
        controls.add(presets, BorderLayout.SOUTH)

        val center = JPanel(BorderLayout(0, 6))
        center.add(listPanel, BorderLayout.CENTER)
        center.add(controls, BorderLayout.SOUTH)

        panel.add(center, BorderLayout.CENTER)
        panel.add(errorLabel, BorderLayout.SOUTH)

        reset()
        return panel
    }

    override fun isModified(): Boolean =
        currentPatterns() != settings.patterns

    override fun apply() {
        val error = validationError()
        if (error != null) {
            showError(error)
            throw ConfigurationException("Invalid template pattern: $error")
        }
        val canonical = HygenTemplateSettings.canonicalize(currentPatterns())
        settings.patterns = canonical
        // Reflect the canonical form back into the list so the panel is no longer "modified".
        model.clear()
        canonical.forEach { model.addElement(it) }
        clearError()
        ApplicationManager.getApplication().messageBus
            .syncPublisher(HygenTemplateSettingsChangeListener.TOPIC)
            .patternsChanged()
    }

    override fun reset() {
        model.clear()
        settings.patterns.forEach { model.addElement(it) }
        clearError()
    }

    override fun disposeUIResources() {
        model.clear()
    }

    // --- editing --------------------------------------------------------------------------

    private fun addPattern() {
        val input = promptForPattern("")
        if (input != null && input.isNotBlank()) {
            model.addElement(input.trim())
            list.selectedIndex = model.size - 1
            refreshValidation()
        }
    }

    private fun editSelected() {
        if (!hasSelection()) return
        val index = list.selectedIndex
        val current = model.get(index)
        val input = promptForPattern(current)
        if (input != null && input.isNotBlank()) {
            model.set(index, input.trim())
            refreshValidation()
        }
    }

    private fun removeSelected() {
        if (!hasSelection()) return
        model.remove(list.selectedIndex)
        refreshValidation()
    }

    private fun moveSelected(offset: Int) {
        val index = list.selectedIndex
        if (index < 0) return
        val target = index + offset
        if (target < 0 || target >= model.size) return
        val item = model.remove(index)
        model.add(target, item)
        list.selectedIndex = target
        refreshValidation()
    }

    private fun applyPreset(preset: String) {
        model.clear()
        model.addElement(preset)
        list.selectedIndex = 0
        refreshValidation()
    }

    // --- helpers --------------------------------------------------------------------------

    private fun promptForPattern(initial: String): String? =
        JOptionPane.showInputDialog(
            errorLabel.topLevelAncestor ?: null,
            "Filename pattern (e.g. *.t, *.ejs.t, *.tmpl):",
            "Template pattern",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            initial,
        ) as String?

    private fun hasSelection(): Boolean {
        if (list.selectedIndex < 0) {
            JOptionPane.showMessageDialog(list, "Select a pattern first.")
            return false
        }
        return true
    }

    /** The raw entries currently in the list. */
    private fun currentPatterns(): List<String> = (0 until model.size).map { model.get(it) }

    /** First validation error across the list, or `null` if every entry is valid. */
    private fun validationError(): String? {
        for (entry in currentPatterns()) {
            val error = HygenPatterns.validate(entry)
            if (error != null) return "$entry — $error"
        }
        return null
    }

    private fun refreshValidation() {
        val error = validationError()
        if (error == null) clearError() else showError(error)
    }

    private fun showError(message: String) {
        errorLabel.text = message
        errorLabel.foreground = Color.RED
        errorLabel.isVisible = true
    }

    private fun clearError() {
        errorLabel.text = " "
        errorLabel.isVisible = false
    }
}
