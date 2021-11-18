package de.christinecoenen.code.zapp.tv.about

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.christinecoenen.code.zapp.R
import de.christinecoenen.code.zapp.databinding.TvAboutItemBinding

class AboutListAdapter : RecyclerView.Adapter<AboutViewViewHolder>() {

	private val aboutItems = listOf(
		AboutItem(R.string.activity_changelog_title, R.drawable.ic_sharp_format_list_bulleted_24),
		AboutItem(R.string.activity_faq_title, R.drawable.ic_baseline_help_outline_24),
	)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AboutViewViewHolder {
		val layoutInflater = LayoutInflater.from(parent.context)
		val binding = TvAboutItemBinding.inflate(layoutInflater, parent, false)
		return AboutViewViewHolder(binding)
	}

	override fun onBindViewHolder(holder: AboutViewViewHolder, position: Int) {
		holder.bind(aboutItems[position])
	}

	override fun getItemCount() = aboutItems.size
}
