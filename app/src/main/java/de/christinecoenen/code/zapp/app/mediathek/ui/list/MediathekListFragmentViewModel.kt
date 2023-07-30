package de.christinecoenen.code.zapp.app.mediathek.ui.list

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import de.christinecoenen.code.zapp.app.mediathek.api.IMediathekApiService
import de.christinecoenen.code.zapp.app.mediathek.api.MediathekPagingSource
import de.christinecoenen.code.zapp.app.mediathek.api.request.QueryRequest
import de.christinecoenen.code.zapp.app.mediathek.api.result.QueryInfoResult
import de.christinecoenen.code.zapp.app.mediathek.ui.list.adapter.UiModel
import de.christinecoenen.code.zapp.app.mediathek.ui.list.filter.models.ChannelFilter
import de.christinecoenen.code.zapp.app.mediathek.ui.list.filter.models.LengthFilter
import de.christinecoenen.code.zapp.models.shows.MediathekShow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import org.joda.time.DateTime

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MediathekListFragmentViewModel(
	private val mediathekApi: IMediathekApiService,
	searchQuery: StateFlow<String>,
	lengthFilter: StateFlow<LengthFilter>,
	channelFilter: StateFlow<ChannelFilter>,
) : ViewModel() {

	companion object {
		private const val ITEM_COUNT_PER_PAGE = 30
		private const val DEBOUNCE_TIME_MILLIS = 300L
	}

	private val pagingConfig = PagingConfig(
		pageSize = ITEM_COUNT_PER_PAGE,
		enablePlaceholders = false
	)

	private val _queryInfoResult = MutableStateFlow<QueryInfoResult?>(null)
	val queryInfoResult = _queryInfoResult.asLiveData()

	val pageFlow = combine(
		searchQuery,
		lengthFilter,
		channelFilter
	) { searchQuery, lengthFilter, channelFilter ->
		createQueryRequest(searchQuery, lengthFilter, channelFilter)
	}
		.debounce(DEBOUNCE_TIME_MILLIS)
		.flatMapLatest { queryRequest ->
			Pager(pagingConfig) {
				MediathekPagingSource(mediathekApi, queryRequest, _queryInfoResult)
			}.flow
		}
		.map<PagingData<MediathekShow>, PagingData<UiModel>> { pagingData ->
			pagingData.map { show ->
				UiModel.MediathekShowModel(
					show,
					DateTime(show.timestamp.toLong() * DateUtils.SECOND_IN_MILLIS)
				)
			}
		}
		.cachedIn(viewModelScope)

	private fun createQueryRequest(
		searchQuery: String,
		lengthFilter: LengthFilter,
		channelFilter: ChannelFilter
	): QueryRequest {
		return QueryRequest().apply {
			size = ITEM_COUNT_PER_PAGE
			minDurationSeconds = lengthFilter.minDurationSeconds
			maxDurationSeconds = lengthFilter.maxDurationSeconds
			setQueryString(searchQuery)
			for (filterItem in channelFilter) {
				setChannel(filterItem.key, filterItem.value)
			}
		}
	}
}
