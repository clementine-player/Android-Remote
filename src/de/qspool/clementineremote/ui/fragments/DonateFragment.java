/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2013, Andreas Muttscheller <asfa194@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package de.qspool.clementineremote.ui.fragments;

import com.actionbarsherlock.app.ActionBar;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.VendingKey;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.utils.IabHelper;
import de.qspool.clementineremote.utils.IabHelper.OnConsumeFinishedListener;
import de.qspool.clementineremote.utils.IabHelper.OnIabPurchaseFinishedListener;
import de.qspool.clementineremote.utils.IabHelper.QueryInventoryFinishedListener;
import de.qspool.clementineremote.utils.IabResult;
import de.qspool.clementineremote.utils.Inventory;
import de.qspool.clementineremote.utils.Purchase;
import de.qspool.clementineremote.utils.Utilities;

public class DonateFragment extends AbstractDrawerFragment {

    private final static String TAG = "DonateFragment";

    private IabHelper mHelper;

    private ActionBar mActionBar;

    private Button mDonateOne;

    private Button mDonateTwo;

    private Button mDonateFive;

    private final static String SKU_ONE_EURO = "one_euro";

    private final static String SKU_TWO_EURO = "two_euros";

    private final static String SKU_FIVE_EURO = "five_euros";

    private boolean mBillingAvailable = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        // Create helper class for in app billing
        mHelper = new IabHelper(getActivity(), VendingKey.getVendingKey());

        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    Log.d("DonateFragment", "Problem setting up In-app Billing: " + result);
                    mBillingAvailable = false;
                } else {
                    mBillingAvailable = true;
                    // Now check for consumable items
                    mHelper.queryInventoryAsync(mGotInventoryListener);
                }
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.donate_fragment,
                container, false);

        mDonateOne = (Button) view.findViewById(R.id.donate_one);
        mDonateTwo = (Button) view.findViewById(R.id.donate_two);
        mDonateFive = (Button) view.findViewById(R.id.donate_five);

        mDonateOne.setOnClickListener(oclDonate);
        mDonateTwo.setOnClickListener(oclDonate);
        mDonateFive.setOnClickListener(oclDonate);

        mActionBar = getSherlockActivity().getSupportActionBar();
        mActionBar.setTitle("");
        mActionBar.setSubtitle("");

        setActionBarTitle();

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Dispose helper class
        if (mHelper != null && mBillingAvailable) {
            mHelper.dispose();
        }
        mHelper = null;
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        switch (clementineMessage.getMessageType()) {
            case CURRENT_METAINFO:
                setActionBarTitle();
                break;
            default:
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        // Pass on the activity result to the helper for handling
        if (!this.mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        Log.d(TAG, "onActivityResult handled by IABUtil.");
    }

    private void setActionBarTitle() {
        MySong currentSong = App.mClementine.getCurrentSong();
        if (currentSong == null) {
            mActionBar.setTitle(getString(R.string.player_nosong));
            mActionBar.setSubtitle("");
        } else {
            mActionBar.setTitle(currentSong.getArtist());
            mActionBar.setSubtitle(currentSong.getTitle());
        }
    }

    private OnClickListener oclDonate = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (!mBillingAvailable) {
                Utilities.ShowMessageDialog(getActivity(), R.string.donate_not_available,
                        R.string.donate_not_available_text);
                return;
            }
            String sku = "";
            if (v.getId() == mDonateOne.getId()) {
                sku = SKU_ONE_EURO;
            } else if (v.getId() == mDonateTwo.getId()) {
                sku = SKU_TWO_EURO;
            } else if (v.getId() == mDonateFive.getId()) {
                sku = SKU_FIVE_EURO;
            }

            try {
                mHelper.launchPurchaseFlow(getActivity(), sku, 10001,
                        mPurchaseFinishedListener, "");
            } catch (IllegalStateException e) {
                Toast.makeText(getActivity(), R.string.donate_try_again, Toast.LENGTH_LONG).show();
            }
        }
    };

    OnIabPurchaseFinishedListener mPurchaseFinishedListener = new OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "mPurchaseFinishedListener " + result);
            if (result.isFailure()) {
                Log.d(TAG, "Error purchasing: " + result);
                return;
            } else {
                // Consume the product directly
                mHelper.consumeAsync(purchase, mConsumeFinishedListener);
            }
        }
    };

    OnConsumeFinishedListener mConsumeFinishedListener = new OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            Log.d(TAG, "mConsumeFinishedListener " + result);
            if (result.isSuccess()) {
                Toast.makeText(getActivity(), R.string.donate_complete, Toast.LENGTH_LONG).show();
            } else {
                Log.d(TAG, "Error consuming product: " + result);
            }
        }
    };

    QueryInventoryFinishedListener mGotInventoryListener = new QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result,
                Inventory inventory) {

            if (result.isFailure()) {
                // handle error here
            } else {
                // Do we have pending donations?
                if (inventory.hasPurchase(SKU_ONE_EURO)) {
                    mHelper.consumeAsync(inventory.getPurchase(SKU_ONE_EURO),
                            mConsumeFinishedListener);
                }

                if (inventory.hasPurchase(SKU_TWO_EURO)) {
                    mHelper.consumeAsync(inventory.getPurchase(SKU_TWO_EURO),
                            mConsumeFinishedListener);
                }

                if (inventory.hasPurchase(SKU_FIVE_EURO)) {
                    mHelper.consumeAsync(inventory.getPurchase(SKU_FIVE_EURO),
                            mConsumeFinishedListener);
                }

            }
        }
    };

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
