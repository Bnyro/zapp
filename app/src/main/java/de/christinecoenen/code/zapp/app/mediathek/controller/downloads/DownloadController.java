package de.christinecoenen.code.zapp.app.mediathek.controller.downloads;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchConfiguration;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.NetworkType;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2.Status;
import com.tonyodev.fetch2core.DownloadBlock;

import org.joda.time.DateTime;

import java.util.List;
import java.util.concurrent.TimeUnit;

import de.christinecoenen.code.zapp.app.mediathek.controller.downloads.exceptions.DownloadException;
import de.christinecoenen.code.zapp.app.mediathek.controller.downloads.exceptions.NoNetworkException;
import de.christinecoenen.code.zapp.app.mediathek.controller.downloads.exceptions.WrongNetworkConditionException;
import de.christinecoenen.code.zapp.app.mediathek.model.DownloadStatus;
import de.christinecoenen.code.zapp.app.mediathek.model.PersistedMediathekShow;
import de.christinecoenen.code.zapp.app.mediathek.model.Quality;
import de.christinecoenen.code.zapp.app.mediathek.repository.MediathekRepository;
import de.christinecoenen.code.zapp.app.settings.repository.SettingsRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;

public class DownloadController implements FetchListener {

	private final Fetch fetch;

	private final ConnectivityManager connectivityManager;
	private final SettingsRepository settingsRepository;
	private final DownloadFileInfoManager downloadFileInfoManager;
	private final MediathekRepository mediathekRepository;

	public DownloadController(Context applicationContext, MediathekRepository mediathekRepository) {
		this.mediathekRepository = mediathekRepository;

		settingsRepository = new SettingsRepository(applicationContext);
		downloadFileInfoManager = new DownloadFileInfoManager(applicationContext, settingsRepository);

		connectivityManager = (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);

		FetchConfiguration fetchConfiguration = new FetchConfiguration.Builder(applicationContext)
			.setNotificationManager(new ZappNotificationManager(applicationContext, mediathekRepository) {
				@NonNull
				@Override
				public Fetch getFetchInstanceForNamespace(@NonNull String namespace) {
					return fetch;
				}
			})
			.enableRetryOnNetworkGain(false)
			.setAutoRetryMaxAttempts(0)
			.build();

		fetch = Fetch.Impl.getInstance(fetchConfiguration);
		fetch.addListener(this);
	}

	public Completable startDownload(PersistedMediathekShow show, Quality quality) {
		return deleteDownloadAsync(show.getId())
			.andThen(Completable.fromAction(() -> startDownloadInternal(show, quality)));
	}

	public void stopDownload(int id) {
		fetch.getDownloadsByRequestIdentifier(id, downloadList -> {
			for (Download download : downloadList) {
				fetch.cancel(download.getId());
			}
		});
	}

	public void deleteDownload(int id) {
		boolean success = deleteDownloadAsync(id).blockingAwait(500, TimeUnit.MILLISECONDS);
		if (!success) {
			throw new RuntimeException("deleteDownloadAsync timed out");
		}
	}

	public Flowable<DownloadStatus> getDownloadStatus(String apiId) {
		return mediathekRepository.getDownloadStatus(apiId);
	}

	public Flowable<Integer> getDownloadProgress(String apiId) {
		return mediathekRepository.getDownloadProgress(apiId);
	}

	public void deleteDownloadsWithDeletedFiles() {
		fetch.getDownloadsWithStatus(Status.COMPLETED, downloads -> {
			for (Download download : downloads) {
				if (downloadFileInfoManager.shouldDeleteDownload(download)) {
					fetch.remove(download.getId());
				}
			}
		});
	}

	private Completable deleteDownloadAsync(int id) {
		return Completable.create(emitter -> fetch.getDownloadsByRequestIdentifier(id, downloadList -> {
			try {
				for (Download download : downloadList) {
					fetch.delete(download.getId());
				}
				emitter.onComplete();
			} catch (Exception e) {
				emitter.onError(e);
			}
		}));
	}

	private void startDownloadInternal(PersistedMediathekShow show, Quality quality) {
		String downloadUrl = show.getMediathekShow().getVideoUrl(quality);
		String filePath = downloadFileInfoManager.getDownloadFilePath(show.getMediathekShow(), quality);

		Request request;
		try {
			request = new Request(downloadUrl, filePath);
			request.setIdentifier(show.getId());
		} catch (Exception e) {
			throw new DownloadException("Constructing download request failed.", e);
		}

		show.setDownloadId(request.getId());
		show.setDownloadedAt(DateTime.now());
		mediathekRepository.updateShow(show);

		enqueueDownload(request);
	}

	private void enqueueDownload(Request request) {
		NetworkType networkType = settingsRepository.getDownloadOverWifiOnly() ?
			NetworkType.WIFI_ONLY : NetworkType.ALL;
		request.setNetworkType(networkType);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager.getActiveNetwork() == null) {
			throw new NoNetworkException("No active network available.");
		}

		if (settingsRepository.getDownloadOverWifiOnly() && connectivityManager.isActiveNetworkMetered()) {
			throw new WrongNetworkConditionException("Download over metered networks prohibited.");
		}

		fetch.enqueue(request, null, null);
	}

	private void updateDownloadStatus(@NonNull Download download) {
		DownloadStatus downloadStatus = DownloadStatus.values()[download.getStatus().getValue()];
		mediathekRepository.updateDownloadStatus(download.getId(), downloadStatus);
	}

	private void updateDownloadProgress(@NonNull Download download, int progress) {
		mediathekRepository.updateDownloadProgress(download.getId(), progress);
	}

	@Override
	public void onAdded(@NonNull Download download) {
		updateDownloadStatus(download);
	}

	@Override
	public void onCancelled(@NonNull Download download) {
		fetch.delete(download.getId());
		updateDownloadStatus(download);
		updateDownloadProgress(download, 0);
	}

	@Override
	public void onCompleted(@NonNull Download download) {
		updateDownloadStatus(download);
		mediathekRepository.updateDownloadedVideoPath(download.getId(), download.getFile());
		downloadFileInfoManager.updateDownloadFileInMediaCollection(download);
	}

	@Override
	public void onDeleted(@NonNull Download download) {
		updateDownloadStatus(download);
		updateDownloadProgress(download, 0);
		downloadFileInfoManager.updateDownloadFileInMediaCollection(download);
	}

	@Override
	public void onDownloadBlockUpdated(@NonNull Download download, @NonNull DownloadBlock downloadBlock, int i) {

	}

	@Override
	public void onError(@NonNull Download download, @NonNull Error error, Throwable throwable) {
		updateDownloadStatus(download);
	}

	@Override
	public void onPaused(@NonNull Download download) {
		updateDownloadStatus(download);
	}

	@Override
	public void onProgress(@NonNull Download download, long l, long l1) {
		updateDownloadProgress(download, download.getProgress());
	}

	@Override
	public void onQueued(@NonNull Download download, boolean b) {
		updateDownloadStatus(download);
	}

	@Override
	public void onRemoved(@NonNull Download download) {
		updateDownloadStatus(download);
	}

	@Override
	public void onResumed(@NonNull Download download) {
		updateDownloadStatus(download);
	}

	@Override
	public void onStarted(@NonNull Download download, @NonNull List<? extends DownloadBlock> list, int i) {
		updateDownloadStatus(download);
	}

	@Override
	public void onWaitingNetwork(@NonNull Download download) {
		updateDownloadStatus(download);
	}
}
