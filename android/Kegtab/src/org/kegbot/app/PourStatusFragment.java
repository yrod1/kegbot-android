/*
 * Copyright 2012 Mike Wakerly <opensource@hoho.com>.
 *
 * This file is part of the Kegtab package from the Kegbot project. For more
 * information on Kegtab or Kegbot, see <http://kegbot.org/>.
 *
 * Kegtab is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, version 2.
 *
 * Kegtab is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Kegtab. If not, see <http://www.gnu.org/licenses/>.
 */
package org.kegbot.app;

import java.util.concurrent.TimeUnit;

import org.kegbot.app.event.FlowUpdateEvent;
import org.kegbot.app.util.ImageDownloader;
import org.kegbot.app.util.Units;
import org.kegbot.app.view.BadgeView;
import org.kegbot.core.AuthenticationManager;
import org.kegbot.core.Flow;
import org.kegbot.core.KegbotCore;
import org.kegbot.proto.Models.Keg;
import org.kegbot.proto.Models.KegTap;

import android.app.Activity;
import android.app.ListFragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.squareup.otto.Subscribe;

public class PourStatusFragment extends ListFragment {

  private static final String TAG = PourStatusFragment.class.getSimpleName();

  private static final double VOLUME_COUNTER_INCREMENT_ML = 10;
  private static final long VOLUME_COUNTER_INCREMENT_DELAY_MILLIS = 20;

  private static final int AUTH_DRINKER_REQUEST = 1;

  private double mTargetVolumeMl = 0.0;
  public double mCurrentVolumeMl = 0.0;

  private KegbotCore mCore;

  private ImageDownloader mImageDownloader;

  private KegTap mTap;  // final after setTap

  private View mView;
  private BadgeView mPourVolumeBadge;
  private TextView mTapTitle;
  private TextView mTapSubtitle;
  private TextView mStatusLine;

  private ImageView mBeerImage;

  private Handler mHandler;

  private final Runnable mCounterIncrementRunnable = new Runnable() {
    @Override
    public void run() {
      final double remain = mTargetVolumeMl - mCurrentVolumeMl;
      if (remain <= 0) {
        return;
      }

      mCurrentVolumeMl += Math.min(remain, VOLUME_COUNTER_INCREMENT_ML);
      setVolumeDisplay(mCurrentVolumeMl);

      if (mCurrentVolumeMl < mTargetVolumeMl) {
        mHandler.postDelayed(mCounterIncrementRunnable, VOLUME_COUNTER_INCREMENT_DELAY_MILLIS);
      }
    }
  };
  public double getCurrentMl()
  {
	  return mCurrentVolumeMl;
  }

  /**
   * After this much inactivity, the "pour automatically ends" dialog is shown.
   */
  private static final long IDLE_TOOLTIP_MILLIS = TimeUnit.SECONDS.toMillis(5);

  private Flow mFlow = null;

  public void setTap(KegTap tap) {
    Preconditions.checkState(mTap == null, "tap already set");
    mTap = tap;
  }

  public KegTap getTap() {
    return mTap;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mCore = KegbotCore.getInstance(getActivity());
    mImageDownloader = mCore.getImageDownloader();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    mView = inflater.inflate(R.layout.pour_status_item_layout, container, false);

    mPourVolumeBadge = (BadgeView) mView.findViewById(R.id.tapStatsBadge1);
    mPourVolumeBadge.setBadgeValue("0.0");
    mPourVolumeBadge.setBadgeCaption("Ounces Poured");

    mTapTitle = (TextView) mView.findViewById(R.id.tapTitle);
    mTapSubtitle = (TextView) mView.findViewById(R.id.tapSubtitle);

    mStatusLine = (TextView) mView.findViewById(R.id.tapNotes);
    mBeerImage = (ImageView) mView.findViewById(R.id.tapImage);

    setVolumeDisplay(0);

    return mView;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case AUTH_DRINKER_REQUEST:
        if (resultCode == Activity.RESULT_OK) {
          final String username =
              data.getStringExtra(KegtabCommon.ACTIVITY_AUTH_DRINKER_RESULT_EXTRA_USERNAME);
          if (!Strings.isNullOrEmpty(username)) {
            Log.d(TAG, "Authenticating async.");
            AuthenticationManager am = mCore.getAuthenticationManager();
            am.authenticateUsernameAsync(username);
          }
        }
        break;
      default:
        super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mHandler = new Handler();
  }

  @Override
  public void onResume() {
    super.onResume();
    applyTapDetail();
    KegbotCore.getInstance(getActivity()).getBus().register(this);
    if (mFlow != null) {
      updateWithFlow(mFlow);
    }
  }

  @Override
  public void onPause() {
    KegbotCore.getInstance(getActivity()).getBus().unregister(this);
    super.onPause();
  }

  @Subscribe
  public void onFlowUpdate(FlowUpdateEvent event) {
    final Flow flow = event.getFlow();
    if (mTap != null && mTap.getMeterName().equals(flow.getTap().getMeterName())) {
      if (flow.isFinished()) {
        setEnded();
      } else {
        updateWithFlow(flow);
      }
    }
  }

  private void setVolumeDisplay(double volumeMl) {
    final Pair<String, String> qty = Units.localizeWithoutScaling(
        mCore.getConfiguration(), volumeMl);
    mPourVolumeBadge.setBadgeValue(qty.first);
    mPourVolumeBadge.setBadgeCaption(Units.capitalizeUnits(qty.second) + " Poured");
  }

  private void applyTapDetail() {
    final KegTap tap = getTap();
    mBeerImage.setImageResource(R.drawable.kegbot_unknown_square_2);

    final Keg keg = tap.getCurrentKeg();

    if (keg != null) {
      final String beerName = keg.getType().getName();

      // Set beer name.
      if (!Strings.isNullOrEmpty(beerName) && mTapTitle != null) {
        mTapTitle.setText(beerName);
      }

      // Set beer image.
      if (keg.getType().hasImage()) {
        mImageDownloader.download(keg.getType().getImage().getUrl(), mBeerImage);
      }
      mTapSubtitle.setText(mTap.getName());
    } else {
      mTapTitle.setText(mTap.getName());
    }
  }

  public void updateWithFlow(Flow flow) {
    Preconditions.checkNotNull(flow, "null flow given to updateWithFlow()");
    mFlow = flow;

    if (mFlow.isFinished()) {
      setEnded();
      return;
    }

    if (mView == null) {
      return;
    }

    // Set volume portion.
    mTargetVolumeMl = flow.getVolumeMl();
    if (mCurrentVolumeMl < mTargetVolumeMl) {
      mHandler.removeCallbacks(mCounterIncrementRunnable);
      mHandler.post(mCounterIncrementRunnable);
    }

    // Update beer info.
    if (flow.getIdleTimeMs() >= IDLE_TOOLTIP_MILLIS) {
      final long seconds = flow.getMsUntilIdle() / 1000;
      mStatusLine.setText("Pour automatically ends in " + seconds + " second"
          + ((seconds != 1) ? "s" : "") + ".");
      mStatusLine.setVisibility(View.VISIBLE);
    } else {
      mStatusLine.setVisibility(View.INVISIBLE);
    }
  }

  /** Marks the tap as ended (no current flow). */
  private void setEnded() {
    if (mView != null) {
      mStatusLine.setText("Pour completed!");
      mStatusLine.setVisibility(View.VISIBLE);
    }
  }

}
