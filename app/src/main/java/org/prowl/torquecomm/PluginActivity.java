package org.prowl.torquecomm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.prowl.torque.remote.ITorqueService;
import org.prowl.torquecomm.utils.PID;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PluginActivity extends Activity {
    private ITorqueService torqueService;

    private TextView textView;

    private TextView miscView;
    private EditText IP;
    private long packetsSent = 0;
    private long packetsReceived = 0;

    private long obdPacketsSent = 0;
    private long obdPacketsReceived = 0;

    private Handler handler;
    private final Vector<PID> pids = new Vector<>();
    private final ScheduledExecutorService singleThreadedExecutor = Executors.newSingleThreadScheduledExecutor();
    private boolean isPaused = false;
    private boolean shouldStartTask = true;

    private final OkHttpClient client = new OkHttpClient().newBuilder()
            .connectTimeout(2000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Initialize views
        textView = findViewById(R.id.packet_data);
        textView.setKeepScreenOn(true);
        miscView = findViewById(R.id.misc_info_data);
        miscView.setKeepScreenOn(true);
        IP = findViewById(R.id.ip_input);

        handler = new Handler();
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d("Resume", "Resumming");
        isPaused = false;

        // Bind to the torque service
        Intent intent = new Intent();
        intent.setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService");
        boolean successfulBind = bindService(intent, connection, 0);


        if (!successfulBind) {
            toast("Unable to connect to Torque plugin service", getApplicationContext());
            handler.post(() -> textView.setText("Unable to connect to Torque plugin service"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
        Log.d("PluginActivity", "Paused");


        // Unbind the service
        unbindService(connection);
    }

    public void populatePIDs() {
        try {
            // <longName>,<shortName>,<unit>,<maxValue>,<minValue>,<scale>
            // obdPacketsSent++;
            String[] mpids = torqueService.listECUSupportedPIDs(); //listECUSupportedPIDs
            String[] pidInfo = torqueService.getPIDInformation(mpids);

            obdPacketsReceived++;

            for (int i = 0; i < mpids.length; i++) {
                String[] info = pidInfo[i].split(",");
                String pidName = mpids[i].split(",")[0];
                PID newPid = new PID(pidName);
                newPid.setFullName(info[0]);
                newPid.setShortName(info[1]);
                newPid.setUnit(info[2]);
                newPid.setUserPid(false);
                pids.add(newPid);
//                Log.d("[PID SUPPORT]", String.format("Name: %s | PID: %s", info[0], pidName));
            }
        } catch (RemoteException e) {
            toast(String.format("RemoteException %s", Objects.requireNonNull(e.getMessage())), getApplicationContext());
        }
    }

    public void runTask() throws RemoteException {
        Optional<PID> RPM = pids.stream().filter(PID -> PID.getPid().equals("0c"))
                .findFirst();

        Optional<PID> IntakeTemp = pids.stream().filter(PID -> PID.getPid().equals("0f"))
                .findFirst();
        Optional<PID> EngineCoolantTemp = pids.stream().filter(PID -> PID.getPid().equals("05"))
                .findFirst();

        Optional<PID> EngineOilTemp = pids.stream().filter(PID -> PID.getPid().equals("5c"))
                .findFirst();
        Optional<PID> IdleDrivingPercent = pids.stream().filter(PID -> PID.getPid().equals("ff1298"))
                .findFirst();

        Optional<PID> IntakeManifoldPressure = pids.stream().filter(PID -> PID.getPid().equals("0b"))
                .findFirst();


        /*
        IntakeTemp.ifPresent(pid -> {
            Log.d("IntakeTemp", String.format("Format: %s | Full Name: %s", pid.getUnit(), pid.getFullName()));
        });
        RPM.ifPresent(pid -> {
            Log.d("RPM", String.format("Format: %s | Full Name: %s", pid.getUnit(), pid.getFullName()));
        });
        EngineCoolantTemp.ifPresent(pid -> {
            Log.d("EngineCoolantTemp", String.format("Format: %s | Full Name: %s", pid.getUnit(), pid.getFullName()));
        });
        EngineOilTemp.ifPresent(pid -> {
            Log.d("EngineOilTemp", String.format("Format: %s | Full Name: %s", pid.getUnit(), pid.getFullName()));
        });
        EngineLoad.ifPresent(pid -> {
            Log.d("EngineLoad", String.format("Format: %s | Full Name: %s", pid.getUnit(), pid.getFullName()));
        });
        IdleDrivingPercent.ifPresent(pid -> {
            Log.d("IdleDrivingPercent", String.format("Format: %s | Full Name: %s", pid.getUnit(), pid.getFullName()));
        });
        */
        EngineOilTemp.ifPresent(pid -> {
            Log.d("EngineOilTemp", String.format("Format: %s | Full Name: %s", pid.getUnit(), pid.getFullName()));
        });

        handler.post(() -> textView.setText(String.format(Locale.ENGLISH, "Packets HRX: %d | Packets HTX: %d   Packets OBDRX: %d | Packets OBDTX: %d", packetsReceived, packetsSent, obdPacketsReceived, obdPacketsSent)));

        double[] values = new double[6];

        assert IntakeManifoldPressure.isPresent() && RPM.isPresent() && EngineOilTemp.isPresent() && EngineCoolantTemp.isPresent() && IntakeTemp.isPresent() && IdleDrivingPercent.isPresent();

        if (torqueService.isConnectedToECU()) {
            try {
                obdPacketsSent++;
                values = torqueService.getPIDValuesAsDouble(new String[]{
                        RPM.get().getPid(),
                        EngineOilTemp.get().getPid(),
                        EngineCoolantTemp.get().getPid(),
                        IntakeTemp.get().getPid(),
                        IdleDrivingPercent.get().getPid(),
                        IntakeManifoldPressure.get().getPid()
                });
                Log.d("PID_VALUES", Arrays.toString(values));
            } catch (RemoteException e) {
                Log.d("GETPIDVALUES", "Coudlnt get pid vales");
            }
            obdPacketsReceived++;
        }


        HttpUrl url = new HttpUrl.Builder()
                .host(IP.getText().toString().isBlank() ? "192.168.69.69" : IP.getText().toString())
                .scheme("http")
                .addPathSegment("test")
                .addQueryParameter("rpm", String.valueOf(values[0]))
                .addQueryParameter("oiltemp", String.valueOf(values[1]))
                .addQueryParameter("cooltanttemp", String.valueOf(values[2]))
                .addQueryParameter("intaketemp", String.valueOf(values[3]))
                .addQueryParameter("idledriving", String.valueOf(values[4]))
                .addQueryParameter("intakeminfoldpressure", String.valueOf(values[5]))
                .build();
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response res = client.newCall(request).execute()) {
            packetsSent++;

            assert res.body() != null;
            Log.d("[HttpRequest][HttpResponse]", res.body().string());
            if (res.code() == 200) {
                packetsReceived++;
            }

        } catch (IOException e) {
            Log.e("[HttpRequest][HttpResponse][HttpError]", Objects.requireNonNull(e.getMessage()));
        } catch (RuntimeException e) {
            Log.e("[HttpRequest][HttpResponse][CodeError]", Objects.requireNonNull(e.getMessage()));
        }

    }

    public void toast(final String message, Context c) {
        final Context context = c;

        handler.post(() -> {
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            } catch (Throwable e) {
            }
        });
    }


    public void popupMessage(final String title, final String message, final boolean finishOnClose) {
        handler.post(() -> {
            try {
                final AlertDialog adialog = new AlertDialog.Builder(PluginActivity.this).create();

                adialog.setButton("OK", (dialog, which) -> {
                    adialog.dismiss();
                    if (finishOnClose) {
                        finish();
                    }
                });

                ScrollView svMessage = new ScrollView(PluginActivity.this);
                TextView tvMessage = new TextView(PluginActivity.this);

                SpannableString spanText = new SpannableString(message);
                Linkify.addLinks(spanText, Linkify.ALL);
                tvMessage.setText(spanText);
                tvMessage.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
                tvMessage.setMovementMethod(LinkMovementMethod.getInstance());

                svMessage.setPadding(14, 2, 10, 12);
                svMessage.addView(tvMessage);

                adialog.setTitle(title);
                adialog.setView(svMessage);
                adialog.show();
            } catch (Throwable ignored) {
            }
        });
    }


    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName arg0, IBinder service) {
            torqueService = ITorqueService.Stub.asInterface(service);

            try {
                if (torqueService.getVersion() < 19) {
                    popupMessage("Incorrect version", "You are using an old version of Torque with this plugin.\n\nThe plugin needs the latest version of Torque to run correctly.\n\nPlease upgrade to the latest version of Torque from Google Play", true);
                    return;
                }

                if (pids.isEmpty()) {
                    toast("Populating PIDS for the first time", getApplicationContext());
                    miscView.setText(String.format("Using %s Protocol", torqueService.getProtocolName().equals("AUTO") ? "N/A" : torqueService.getProtocolName()));
                    IP.setText("192.168.69.69"); //Default IP
                    populatePIDs();


                }
                if (shouldStartTask) {
                    shouldStartTask = false;
                    singleThreadedExecutor.scheduleWithFixedDelay(() -> {
                        if (!isPaused) {
                            try {
                                runTask();
                            } catch (RemoteException e) {
                                Log.e("Rmote exception", Objects.requireNonNull(e.getMessage()));
                            } catch (RuntimeException e) {
                                Log.e("Runtime exception", Objects.requireNonNull(e.getMessage()));
                            }
                        }

                    }, 0, 1000, TimeUnit.MILLISECONDS);
                }

            } catch (RemoteException e) {
                Log.e("PluginActivity", "Error checking Torque version: " + e.getMessage());
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            torqueService = null;
        }
    };
}