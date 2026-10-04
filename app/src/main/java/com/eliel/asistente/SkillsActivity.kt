package com.eliel.asistente

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class SkillsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var summaryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skills)

        container = findViewById(R.id.skillsContainer)
        summaryText = findViewById(R.id.skillsSummaryText)

        renderSkills()
    }

    override fun onResume() {
        super.onResume()
        refreshSummary()
    }

    private fun renderSkills() {
        container.removeAllViews()

        NexoSkillPolicy.definitions.forEach { skill ->
            val card = MaterialCardView(this).apply {
                radius = resources.getDimension(R.dimen.nexo_card_radius)
                cardElevation = 0f
                strokeWidth = 1
                setStrokeColor(getColor(R.color.nexo_outline))
                setCardBackgroundColor(getColor(R.color.nexo_surface))
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
            }

            val toggle = SwitchMaterial(this).apply {
                text = skill.name
                textSize = 16f
                setTextColor(getColor(R.color.nexo_text))
                isChecked = NexoSkillPolicy.isEnabled(this@SkillsActivity, skill.id)
                setOnCheckedChangeListener { _, enabled ->
                    NexoSkillPolicy.setEnabled(this@SkillsActivity, skill.id, enabled)
                    refreshSummary()
                }
            }

            val detail = TextView(this).apply {
                text = skill.description
                textSize = 13f
                setTextColor(getColor(R.color.nexo_text_muted))
                setPadding(0, 6.dp(), 0, 0)
            }

            row.addView(toggle)
            row.addView(detail)
            card.addView(row)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dp()
            }
            container.addView(card, params)
        }

        refreshSummary()
    }

    private fun refreshSummary() {
        val enabled = NexoSkillPolicy.enabledNames(this)
        summaryText.text = enabled.size.toString() + " de " +
            NexoSkillPolicy.definitions.size + " capacidades activas"
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()
}
