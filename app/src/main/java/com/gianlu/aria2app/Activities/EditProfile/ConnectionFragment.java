package com.gianlu.aria2app.Activities.EditProfile;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gianlu.aria2app.CountryFlags;
import com.gianlu.aria2app.NetIO.CertUtils;
import com.gianlu.aria2app.NetIO.FreeGeoIP.FreeGeoIPApi;
import com.gianlu.aria2app.NetIO.FreeGeoIP.IPDetails;
import com.gianlu.aria2app.NetIO.NetUtils;
import com.gianlu.aria2app.ProfilesManager.MultiProfile;
import com.gianlu.aria2app.R;
import com.gianlu.aria2app.Utils;
import com.gianlu.commonutils.AskPermission;
import com.gianlu.commonutils.CommonUtils;
import com.gianlu.commonutils.Logging;
import com.gianlu.commonutils.SuperTextView;
import com.gianlu.commonutils.Toaster;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.security.auth.x500.X500Principal;

public class ConnectionFragment extends FieldErrorFragment {
    private final static int CODE_PICK_CERT = 1;
    private static final int READ_PERMISSION_CODE = 11;
    private final CountryFlags flags = CountryFlags.get();
    private ScrollView layout;
    private TextView completeAddress;
    private RadioGroup connectionMethod;
    private TextInputLayout address;
    private TextInputLayout port;
    private TextInputLayout endpoint;
    private CheckBox encryption;
    private LinearLayout certificateSelectionContainer;
    private LinearLayout certificateDetailsContainer;
    private X509Certificate lastLoadedCertificate;
    private CheckBox hostnameVerifier;
    private SuperTextView certificateDetailsVersion;
    private SuperTextView certificateDetailsSerialNumber;
    private SuperTextView certificateDetailsSigAlgName;
    private SuperTextView certificateDetailsSigAlgOid;
    private SuperTextView certificateDetailsIssuerName;
    private SuperTextView certificateDetailsIssuerAns;
    private SuperTextView certificateDetailsSubjectAns;
    private SuperTextView certificateDetailsSubjectName;
    private ImageView addressFlag;

    @NonNull
    public static ConnectionFragment getInstance(Context context, @Nullable MultiProfile.UserProfile edit) {
        ConnectionFragment fragment = new ConnectionFragment();
        fragment.setRetainInstance(true);
        Bundle args = new Bundle();
        args.putString("title", context.getString(R.string.connection));
        if (edit != null) args.putSerializable("edit", edit);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        layout = (ScrollView) inflater.inflate(R.layout.fragment_edit_profile_connection, container, false);
        completeAddress = layout.findViewById(R.id.editProfile_completeAddress);
        addressFlag = layout.findViewById(R.id.editProfile_addressFlag);
        connectionMethod = layout.findViewById(R.id.editProfile_connectionMethod);
        connectionMethod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
                updateCompleteAddress();
            }
        });
        address = layout.findViewById(R.id.editProfile_address);
        CommonUtils.getEditText(address).addTextChangedListener(new TextWatcher() {
            private final Timer timer = new Timer();
            private TimerTask task;
            private String lastAddress;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                address.setErrorEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateCompleteAddress();
                lastAddress = s.toString();

                if (task != null) task.cancel();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (lastAddress != null) {
                            FreeGeoIPApi.get().getIPDetails(lastAddress, new FreeGeoIPApi.OnIpDetails() {
                                @Override
                                public void onDetails(@NonNull IPDetails details) {
                                    if (isAdded()) {
                                        addressFlag.setVisibility(View.VISIBLE);
                                        addressFlag.setImageDrawable(flags.loadFlag(getContext(), details.countryCode));
                                    }
                                }

                                @Override
                                public void onException(@NonNull Exception ex) {
                                    addressFlag.setVisibility(View.GONE);
                                    Logging.log(ex);
                                }
                            });
                        }
                    }
                };

                timer.schedule(task, 500);
            }
        });
        port = layout.findViewById(R.id.editProfile_port);
        CommonUtils.getEditText(port).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                port.setErrorEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateCompleteAddress();
            }
        });
        endpoint = layout.findViewById(R.id.editProfile_endpoint);
        CommonUtils.getEditText(endpoint).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                endpoint.setErrorEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateCompleteAddress();
            }
        });
        encryption = layout.findViewById(R.id.editProfile_encryption);
        encryption.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                certificateSelectionContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                updateCompleteAddress();
            }
        });

        certificateSelectionContainer = layout.findViewById(R.id.editProfile_certificateSelectionContainer);
        certificateDetailsContainer = layout.findViewById(R.id.editProfile_certificateDetailsContainer);
        certificateDetailsSigAlgName = layout.findViewById(R.id.editProfile_certificateDetails_sigAlgName);
        certificateDetailsSigAlgOid = layout.findViewById(R.id.editProfile_certificateDetails_sigAlgOid);
        certificateDetailsIssuerName = layout.findViewById(R.id.editProfile_certificateDetails_issuerName);
        certificateDetailsIssuerAns = layout.findViewById(R.id.editProfile_certificateDetails_issuerAns);
        certificateDetailsSubjectName = layout.findViewById(R.id.editProfile_certificateDetails_subjectName);
        certificateDetailsSubjectAns = layout.findViewById(R.id.editProfile_certificateDetails_subjectAns);

        certificateDetailsVersion = layout.findViewById(R.id.editProfile_certificateDetails_version);
        certificateDetailsSerialNumber = layout.findViewById(R.id.editProfile_certificateDetails_serialNumber);

        hostnameVerifier = layout.findViewById(R.id.editProfile_hostnameVerifier);

        ImageButton pickCertificateFile = layout.findViewById(R.id.editProfile_pickCertificateFile);
        pickCertificateFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() == null) return;

                AskPermission.ask(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE, new AskPermission.Listener() {
                    @Override
                    public void permissionGranted(@NonNull String permission) {
                        showCertPicker();
                    }

                    @Override
                    public void permissionDenied(@NonNull String permission) {
                        showToast(Toaster.build().message(R.string.readPermissionDenied).error(true));
                    }

                    @Override
                    public void askRationale(@NonNull AlertDialog.Builder builder) {
                        builder.setTitle(R.string.readExternalStorageRequest_title)
                                .setMessage(R.string.readExternalStorageRequest_certMessage);
                    }
                });
            }
        });

        ImageButton removeCertificateFile = layout.findViewById(R.id.editProfile_removeCertificateFile);
        removeCertificateFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadCertificateUri(null);
            }
        });

        Bundle args = getArguments();
        MultiProfile.UserProfile edit;
        if (args != null && (edit = (MultiProfile.UserProfile) args.getSerializable("edit")) != null) {
            switch (edit.connectionMethod) {
                default:
                case HTTP:
                    connectionMethod.check(R.id.editProfile_connectionMethod_http);
                    break;
                case WEBSOCKET:
                    connectionMethod.check(R.id.editProfile_connectionMethod_ws);
                    break;
            }

            CommonUtils.setText(address, edit.serverAddr);
            CommonUtils.setText(port, String.valueOf(edit.serverPort));
            CommonUtils.setText(endpoint, edit.serverEndpoint);
            encryption.setChecked(edit.serverSSL);
            hostnameVerifier.setChecked(edit.hostnameVerifier);
            if (edit.serverSSL && edit.certificate != null)
                showCertificateDetails(edit.certificate);

            lastLoadedCertificate = edit.certificate;
        }

        created = true;

        return layout;
    }

    private void showCertPicker() {
        try {
            startActivityForResult(Intent.createChooser(new Intent(Intent.ACTION_GET_CONTENT)
                    .setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE), "Select the certificate"), CODE_PICK_CERT);
        } catch (ActivityNotFoundException ex) {
            Logging.log(ex);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == READ_PERMISSION_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showCertPicker();
            return;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CODE_PICK_CERT && resultCode == Activity.RESULT_OK && isAdded())
            loadCertificateUri(data.getData());
    }

    private void showCertificateDetails(@NonNull X509Certificate certificate) {
        certificateDetailsContainer.setVisibility(View.VISIBLE);

        certificateDetailsVersion.setHtml(R.string.version, String.valueOf(certificate.getVersion()));
        certificateDetailsSerialNumber.setHtml(R.string.serialNumber, Utils.toHexString(certificate.getSerialNumber().toByteArray()));

        certificateDetailsSigAlgName.setHtml(R.string.name, certificate.getSigAlgName());
        certificateDetailsSigAlgOid.setHtml(R.string.oid, certificate.getSigAlgOID());

        certificateDetailsIssuerName.setHtml(R.string.name, certificate.getIssuerX500Principal().getName(X500Principal.RFC1779));

        try {
            Collection<List<?>> ians = certificate.getIssuerAlternativeNames();
            if (ians == null) {
                certificateDetailsIssuerAns.setVisibility(View.GONE);
            } else {
                certificateDetailsIssuerAns.setVisibility(View.VISIBLE);
                List<CertUtils.GeneralName> names = CertUtils.parseGeneralNames(ians);
                certificateDetailsIssuerAns.setHtml(R.string.alternativeNames, CommonUtils.join(names, ", "));
            }
        } catch (CertificateParsingException ex) {
            certificateDetailsIssuerAns.setVisibility(View.GONE);
            Logging.log(ex);
        }

        certificateDetailsSubjectName.setHtml(R.string.name, certificate.getSubjectX500Principal().getName(X500Principal.RFC1779));

        try {
            Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
            if (sans == null) {
                certificateDetailsSubjectAns.setVisibility(View.GONE);
            } else {
                certificateDetailsSubjectAns.setVisibility(View.VISIBLE);
                List<CertUtils.GeneralName> names = CertUtils.parseGeneralNames(sans);
                certificateDetailsSubjectAns.setHtml(R.string.alternativeNames, CommonUtils.join(names, ", "));
            }
        } catch (CertificateParsingException ex) {
            certificateDetailsSubjectAns.setVisibility(View.GONE);
            Logging.log(ex);
        }
    }

    private void loadCertificateUri(@Nullable Uri path) {
        if (path == null) {
            lastLoadedCertificate = null;
            certificateDetailsContainer.setVisibility(View.GONE);
            return;
        }

        if (getContext() == null) return;

        X509Certificate certificate;
        try {
            InputStream in = getContext().getContentResolver().openInputStream(path);
            if (in != null) {
                certificate = CertUtils.loadCertificateFromStream(in);
            } else {
                showToast(Toaster.build().message(R.string.invalidCertificate).ex(new NullPointerException("InputStream is null!")));
                return;
            }
        } catch (FileNotFoundException | CertificateException ex) {
            showToast(Toaster.build().message(R.string.invalidCertificate).ex(ex));
            return;
        }

        lastLoadedCertificate = certificate;
        showCertificateDetails(certificate);
    }

    private void updateCompleteAddress() {
        if (!isAdded()) return;

        try {
            Fields fields = getFields(getContext(), true);

            String protocol;
            switch (fields.connectionMethod) {
                case WEBSOCKET:
                    protocol = fields.encryption ? "wss" : "ws";
                    break;
                default:
                case HTTP:
                    protocol = fields.encryption ? "https" : "http";
                    break;
            }

            URI url = new URI(protocol, null, fields.address, fields.port, fields.endpoint, null, null);
            completeAddress.setText(url.toString());
            completeAddress.setVisibility(View.VISIBLE);
        } catch (InvalidFieldException | URISyntaxException | NullPointerException ex) {
            completeAddress.setVisibility(View.GONE);
        }
    }

    public Fields getFields(Context context, boolean partial) throws InvalidFieldException {
        if (!created || !isAdded()) {
            Bundle args = getArguments();
            MultiProfile.UserProfile edit;
            if (args == null || (edit = (MultiProfile.UserProfile) getArguments().getSerializable("edit")) == null)
                return null;
            else
                return new Fields(edit.connectionMethod, edit.serverAddr, edit.serverPort, edit.serverEndpoint, edit.serverSSL, edit.certificate, edit.hostnameVerifier);
        }

        MultiProfile.ConnectionMethod connectionMethod;
        switch (this.connectionMethod.getCheckedRadioButtonId()) {
            case R.id.editProfile_connectionMethod_ws:
                connectionMethod = MultiProfile.ConnectionMethod.WEBSOCKET;
                break;
            default:
            case R.id.editProfile_connectionMethod_http:
                connectionMethod = MultiProfile.ConnectionMethod.HTTP;
                break;
        }

        String address = CommonUtils.getText(this.address).trim();
        if (address.isEmpty())
            throw new InvalidFieldException(getClass(), R.id.editProfile_address, context.getString(R.string.addressEmpty));

        int port;
        try {
            port = Integer.parseInt(CommonUtils.getText(this.port).trim());
            if (port <= 0 || port > 65535) throw new Exception();
        } catch (Exception ex) {
            throw new InvalidFieldException(getClass(), R.id.editProfile_port, context.getString(R.string.invalidPort));
        }

        String endpoint = CommonUtils.getText(this.endpoint).trim();
        if (endpoint.isEmpty())
            throw new InvalidFieldException(getClass(), R.id.editProfile_endpoint, context.getString(R.string.endpointEmpty));

        boolean encryption = this.encryption.isChecked();

        if (!NetUtils.isUrlValid(address, port, endpoint, encryption))
            throw new InvalidFieldException(getClass(), R.id.editProfile_address, getString(R.string.invalidCompleteAddress));

        if (partial)
            return new Fields(connectionMethod, address, port, endpoint, encryption, null, false);

        return new Fields(connectionMethod, address, port, endpoint, encryption, lastLoadedCertificate, hostnameVerifier.isChecked());
    }

    @Override
    public void onFieldError(@IdRes int fieldId, String reason) {
        TextInputLayout inputLayout = layout.findViewById(fieldId);
        if (inputLayout != null) {
            inputLayout.setErrorEnabled(true);
            inputLayout.setError(reason);
        }
    }

    public class Fields {
        public final MultiProfile.ConnectionMethod connectionMethod;
        public final String address;
        public final int port;
        public final String endpoint;
        public final boolean encryption;
        public final X509Certificate certificate;
        public final boolean hostnameVerifier;

        Fields(MultiProfile.ConnectionMethod connectionMethod, String address, int port, String endpoint, boolean encryption, @Nullable X509Certificate certificate, boolean hostnameVerifier) {
            this.connectionMethod = connectionMethod;
            this.address = address;
            this.port = port;
            this.endpoint = endpoint;
            this.encryption = encryption;
            this.certificate = certificate;
            this.hostnameVerifier = hostnameVerifier;
        }
    }
}
