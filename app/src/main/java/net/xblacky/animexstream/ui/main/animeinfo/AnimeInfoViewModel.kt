package net.xblacky.animexstream.ui.main.animeinfo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableObserver
import net.xblacky.animexstream.utils.CommonViewModel
import net.xblacky.animexstream.utils.constants.Const
import net.xblacky.animexstream.utils.model.AnimeInfoModel
import net.xblacky.animexstream.utils.model.EpisodeModel
import net.xblacky.animexstream.utils.model.FavouriteModel
import net.xblacky.animexstream.utils.parser.HtmlParser
import okhttp3.ResponseBody

class AnimeInfoViewModel(private val categoryUrl: String) : CommonViewModel() {

    private var _animeInfoModel: MutableLiveData<AnimeInfoModel> = MutableLiveData()
    private var _episodeList: MutableLiveData<ArrayList<EpisodeModel>> = MutableLiveData()
    var episodeList: LiveData<ArrayList<EpisodeModel>> = _episodeList
    var animeInfoModel: LiveData<AnimeInfoModel> = _animeInfoModel
    private val animeInfoRepository = AnimeInfoRepository()
    private var compositeDisposable = CompositeDisposable()
    private var _isFavourite: MutableLiveData<Boolean> = MutableLiveData(false)
    var isFavourite: LiveData<Boolean> = _isFavourite

    init {
        fetchAnimeInfo()
    }

    private fun fetchAnimeInfo() {
        updateLoading(loading = true)
        updateErrorModel(false, null, false)
        categoryUrl.let {
            compositeDisposable.add(
                animeInfoRepository.fetchAnimeInfo(it)
                    .subscribeWith(getAnimeInfoObserver(Const.TYPE_ANIME_INFO))
            )
        }
    }

    private fun getAnimeInfoObserver(typeValue: Int): DisposableObserver<ResponseBody> {
        return object : DisposableObserver<ResponseBody>() {
            override fun onNext(response: ResponseBody) {
                if (typeValue == Const.TYPE_ANIME_INFO) {
                    val animeInfoModel = HtmlParser.parseAnimeInfo(response = response.string())
                    _animeInfoModel.value = animeInfoModel
                    compositeDisposable.add(
                        animeInfoRepository.fetchEpisodeList(
                            id = animeInfoModel.id,
                            endEpisode = animeInfoModel.endEpisode,
                            alias = animeInfoModel.alias
                        )
                            .subscribeWith(getAnimeInfoObserver(Const.TYPE_EPISODE_LIST))
                    )
                    _isFavourite.value = animeInfoRepository.isFavourite(animeInfoModel.id)


                } else if (typeValue == Const.TYPE_EPISODE_LIST) {
                    _episodeList.value = HtmlParser.fetchEpisodeList(response = response.string())
                    updateLoading(loading = false)

                }
            }

            override fun onComplete() {

            }

            override fun onError(e: Throwable) {
                updateLoading(loading = false)
                if (typeValue == Const.TYPE_ANIME_INFO) {
                    updateErrorModel(show = true, e = e, isListEmpty = false)
                } else {
                    updateErrorModel(show = true, e = e, isListEmpty = true)
                }

            }

        }
    }

    fun toggleFavourite() {
        if (_isFavourite.value!!) {
            animeInfoModel.value?.id?.let { animeInfoRepository.removeFromFavourite(it) }
            _isFavourite.value = false
        } else {
            saveFavourite()
        }
    }

    private fun saveFavourite() {
        val model = animeInfoModel.value
        animeInfoRepository.addToFavourite(
            FavouriteModel(
                ID = model?.id,
                categoryUrl = categoryUrl,
                animeName = model?.animeTitle,
                releasedDate = model?.releasedTime,
                imageUrl = model?.imageUrl
            )
        )
        _isFavourite.value = true
    }

//    fun setUrl(url: String) {
//        this.categoryUrl = url
//    }

    override fun onCleared() {
        if (!compositeDisposable.isDisposed) {
            compositeDisposable.dispose()
        }
        if (isFavourite.value!!) {
            saveFavourite()
        }
        super.onCleared()
    }
}