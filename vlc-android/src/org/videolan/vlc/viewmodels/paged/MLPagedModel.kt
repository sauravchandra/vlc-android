package org.videolan.vlc.viewmodels.paged

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import androidx.paging.PositionalDataSource
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.viewmodels.SortableModel

abstract class MLPagedModel<T : MediaLibraryItem>(context: Context) : SortableModel(context), Medialibrary.OnMedialibraryReadyListener {
    protected val medialibrary = Medialibrary.getInstance()
    protected var filter : String? = null
    val loading = MutableLiveData<Boolean>().apply { value = false }

    private val pagingConfig = PagedList.Config.Builder()
            .setPageSize(100)
            .setPrefetchDistance(100)
            .setEnablePlaceholders(true)
            .build()!!

    val pagedList = LivePagedListBuilder(MLDatasourceFactory(), pagingConfig)
            .build()

    init {
        @Suppress("LeakingThis")
        medialibrary.addOnMedialibraryReadyListener(this)
        if (medialibrary.isStarted) onMedialibraryReady()
    }

    override fun onMedialibraryReady() {
        launch { refresh() }
    }

    override fun onMedialibraryIdle() {
        launch { refresh() }
    }

    override fun onCleared() {
        medialibrary.removeOnMedialibraryReadyListener(this)
        super.onCleared()
    }

    abstract fun getTotalCount() : Int
    abstract fun getPage(loadSize: Int, startposition: Int) : Array<T>
    abstract fun getAll() : Array<T>

    override fun sort(sort: Int) {
        if (this.sort != sort) {
            this.sort = sort
            desc = false
        } else desc = !desc
        refresh()
    }

    fun isFiltering() = filter != null

    override fun filter(query: String?) {
        filter = query
        refresh()
    }

    override fun restore() {
        if (filter != null) {
            filter = null
            refresh()
        }
    }

    override fun refresh(): Boolean {
        if (pagedList.value?.dataSource?.isInvalid == false) {
            loading.postValue(true)
            pagedList.value?.dataSource?.invalidate()
        }
        return true
    }

    inner class MLDataSource : PositionalDataSource<T>() {

        override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<T>) {
            val page = getPage(params.requestedLoadSize, params.requestedStartPosition)
            val count = if (page.size < params.requestedLoadSize) page.size else getTotalCount()
            callback.onResult(page.toList(), params.requestedStartPosition, count)
            loading.postValue(false)
        }
        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
            callback.onResult(getPage(params.loadSize, params.startPosition).toList())
        }
    }

    inner class MLDatasourceFactory : DataSource.Factory<Int, T>() {
        override fun create() = MLDataSource()
    }
}
