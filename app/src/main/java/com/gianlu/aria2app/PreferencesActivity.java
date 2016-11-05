package com.gianlu.aria2app;

import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v7.app.AlertDialog;

import com.android.vending.billing.IInAppBillingService;
import com.gianlu.aria2app.Google.Analytics;
import com.gianlu.aria2app.Google.Billing.Billing;
import com.gianlu.aria2app.Google.Billing.Product;
import com.gianlu.aria2app.Google.Billing.ProductAdapter;
import com.gianlu.aria2app.Google.Billing.PurchasedProduct;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.LogsActivity;
import com.google.android.gms.analytics.HitBuilders;

import org.json.JSONException;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public class PreferencesActivity extends PreferenceActivity {
    private IInAppBillingService billingService;
    private ProgressDialog pd;
    private int requestCode;
    private String devString;
    private ServiceConnection serviceConnection;

    @Override
    protected void onStart() {
        super.onStart();

        if (billingService == null) {
            serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    billingService = null;
                }

                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    billingService = IInAppBillingService.Stub.asInterface(service);
                    if (pd != null && pd.isShowing()) {
                        donate();
                    }
                }
            };

            bindService(new Intent("com.android.vending.billing.InAppBillingService.BIND").setPackage("com.android.vending"),
                    serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.main_pref);
        pd = CommonUtils.fastIndeterminateProgressDialog(this, R.string.connectingBillingService);

        findPreference("email").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("message/rfc822");
                i.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.email)});
                i.putExtra(Intent.EXTRA_SUBJECT, "Aria2App");
                i.putExtra(Intent.EXTRA_TEXT, "OS Version: " + System.getProperty("os.version") + "(" + android.os.Build.VERSION.INCREMENTAL + ")" +
                        "\nOS API Level: " + android.os.Build.VERSION.SDK_INT +
                        "\nDevice: " + android.os.Build.DEVICE +
                        "\nModel (and Product): " + android.os.Build.MODEL + " (" + android.os.Build.PRODUCT + ")");
                try {
                    startActivity(Intent.createChooser(i, "Send mail to the developer..."));
                } catch (android.content.ActivityNotFoundException ex) {
                    CommonUtils.UIToast(PreferencesActivity.this, Utils.ToastMessages.NO_EMAIL_CLIENT);
                }
                return true;
            }
        });

        try {
            findPreference("app_version").setSummary(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException ex) {
            findPreference("app_version").setSummary(R.string.unknown);
        }

        findPreference("logs").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(PreferencesActivity.this, LogsActivity.class));
                return true;
            }
        });

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });

        findPreference("nv-websocket-client").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                CommonUtils.showDialog(PreferencesActivity.this, builder
                        .setTitle("nv-websocket-client")
                        .setMessage(R.string.nv_websocket_client_license));
                return true;
            }
        });

        findPreference("mpAndroidChart").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                CommonUtils.showDialog(PreferencesActivity.this, builder
                        .setTitle("MPAndroidChart")
                        .setMessage(R.string.mpAndroidChart_details));
                return true;
            }
        });

        findPreference("apacheLicense").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.apache.org/licenses/LICENSE-2.0")));
                return true;
            }
        });

        findPreference("donate").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                donate();
                return true;
            }
        });
    }

    private void donate() {
        if (billingService == null) {
            CommonUtils.showDialog(this, pd);
            return;
        }

        Billing.requestProductsDetails(this, billingService, Billing.donationProducts, new Billing.IRequestProductDetails() {
            @Override
            public void onReceivedDetails(final Billing.IRequestProductDetails handler, final List<Product> products) {
                final Billing.IBuyProduct buyHandler = new Billing.IBuyProduct() {
                    @Override
                    public void onGotIntent(PendingIntent intent, String developerString) {
                        devString = developerString;
                        requestCode = new Random().nextInt();

                        try {
                            PreferencesActivity.this.startIntentSenderForResult(intent.getIntentSender(), requestCode, new Intent(), 0, 0, 0);
                        } catch (IntentSender.SendIntentException ex) {
                            CommonUtils.UIToast(PreferencesActivity.this, Utils.ToastMessages.FAILED_CONNECTION_BILLING_SERVICE, ex);
                        }
                    }

                    @Override
                    public void onAPIException(int code) {
                        handler.onAPIException(code);
                    }

                    @Override
                    public void onUserCancelled() {
                        handler.onUserCancelled();
                    }

                    @Override
                    public void onFailed(Exception ex) {
                        CommonUtils.UIToast(PreferencesActivity.this, Utils.ToastMessages.FAILED_CONNECTION_BILLING_SERVICE, ex);
                    }
                };
                pd.dismiss();

                CommonUtils.showDialog(PreferencesActivity.this, new AlertDialog.Builder(PreferencesActivity.this)
                        .setTitle(getString(R.string.donate))
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                            }
                        })
                        .setAdapter(new ProductAdapter(PreferencesActivity.this, products, new ProductAdapter.IAdapter() {
                            @Override
                            public void onItemSelected(Product product) {
                                Billing.buyProduct(PreferencesActivity.this, billingService, product, buyHandler);
                            }
                        }), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Billing.buyProduct(PreferencesActivity.this, billingService, products.get(i), buyHandler);
                            }
                        }));

                if (Analytics.isTrackingAllowed(PreferencesActivity.this))
                    Analytics.getDefaultTracker(PreferencesActivity.this.getApplication()).send(new HitBuilders.EventBuilder()
                            .setCategory(Analytics.CATEGORY_USER_INPUT)
                            .setAction(Analytics.ACTION_DONATE_OPEN)
                            .build());
            }

            @Override
            public void onAPIException(int code) {
                if (code == Billing.RESULT_BILLING_UNAVAILABLE)
                    CommonUtils.UIToast(PreferencesActivity.this, Utils.ToastMessages.FAILED_CONNECTION_BILLING_SERVICE, "Code: " + code);
                else
                    CommonUtils.UIToast(PreferencesActivity.this, Utils.ToastMessages.FAILED_BUYING_ITEM, "Code: " + code);
            }

            @Override
            public void onUserCancelled() {
                CommonUtils.UIToast(PreferencesActivity.this, Utils.ToastMessages.BILLING_USER_CANCELLED);
            }

            @Override
            public void onFailed(Exception ex) {
                CommonUtils.UIToast(PreferencesActivity.this, Utils.ToastMessages.FAILED_CONNECTION_BILLING_SERVICE, ex);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (billingService != null)
            unbindService(serviceConnection);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == this.requestCode) {
            if (data.getIntExtra("RESPONSE_CODE", RESULT_CANCELED) == RESULT_OK) {
                try {
                    PurchasedProduct purchasedProduct = new PurchasedProduct(data.getStringExtra("INAPP_PURCHASE_DATA"));
                    if (Objects.equals(purchasedProduct.developerPayload, devString)) {
                        if (purchasedProduct.purchaseState == PurchasedProduct.PURCHASED) {
                            CommonUtils.UIToast(this, Utils.ToastMessages.THANK_YOU, "Purchased " + purchasedProduct.productId + " with order ID " + purchasedProduct.orderId);
                        } else if (purchasedProduct.purchaseState == PurchasedProduct.CANCELED) {
                            CommonUtils.UIToast(this, Utils.ToastMessages.PURCHASING_CANCELED);
                        }
                    } else {
                        CommonUtils.UIToast(this, Utils.ToastMessages.FAILED_BUYING_ITEM, new Exception("Payloads mismatch!"));
                    }
                } catch (JSONException ex) {
                    CommonUtils.UIToast(this, Utils.ToastMessages.FAILED_BUYING_ITEM, ex);
                }
            } else {
                CommonUtils.UIToast(this, Utils.ToastMessages.PURCHASING_CANCELED);
            }
        }

    }
}
