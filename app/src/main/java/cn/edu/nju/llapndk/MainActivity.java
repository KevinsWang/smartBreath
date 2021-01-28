package cn.edu.nju.llapndk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.Message;

import java.io.*;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private Button btnPlayRecord;

    private Button btnStopRecord;

    private TextView texDistance_x;

    private TextView texDistance_y;


    private TraceView mytrace;

    private int recBufSize = 0;

    private int frameSize = 512;

    private double disx, disy;

    private double displaydis = 0;

    private String sysname = "llap";

    private AudioRecord audioRecord;


    private double temperature = 20;

    private double freqinter = 350;

    private int numfreq  = 16;

    private double[] wavefreqs = new double[numfreq];

    private double[] wavelength = new double[numfreq];

    private double[] phasechange = new double[numfreq * 2];

    private double[] freqpower = new double[numfreq * 2];

    private double[] dischange = new double[2];

    private double[] idftdis = new double[2];

    private double startfreq = 15050;//17150

    private double soundspeed = 0;

    private int playBufSize;

    private boolean sendDatatoMatlab = false;
    private boolean sendbaseband = false;
    private boolean logenabled = true;
    private boolean sendDataToFile = false;
    /**
     *
     */
    private boolean blnPlayRecord = false;

    private int coscycle = 1920;

    /**
     *
     */
    //private int sampleRateInHz = 44100;
    private int sampleRateInHz = 48000;

    /**
     *
     */
    //private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;

    /**
     *
     */
    private int encodingBitrate = AudioFormat.ENCODING_PCM_16BIT;

    private int cicdec = 16;
    private int cicsec = 3;
    private int cicdelay = cicdec * 17;


    private double[] baseband = new double[2 * numfreq * 2 * frameSize / cicdec];

    private double[] baseband_nodc = new double[2 * numfreq * 2 * frameSize / cicdec];

    private short[] dcvalue = new short[4 * numfreq];


    private int[] trace_x = new int[1000];
    private int[] trace_y = new int[1000];
    private int tracecount = 0;


    private boolean isCalibrated = false;
    private int now;
    private int lastcalibration;

    private double distrend = 0.05;

    private double micdis1 = 5;
    private double micdis2 = 115;
    private double dischangehist = 0;

    File filex, filey, handlerWavFile;
    private final int REQUEST_EXTERNAL_STORAGE = 1;
    /**
     *
     */

    private Socket datasocket;
    private OutputStream datastream;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //新建disx.txt和disy.txt和audioRecord.pcm文件
        verifyStoragePermissions(this);
        File extDir = Environment.getExternalStorageDirectory();
        String timestamp = "" + System.currentTimeMillis();
        String xname = "disx_" + timestamp + ".txt";
        String yname = "disy_" + timestamp + ".txt";
        filex = new File(extDir, xname);
        filey = new File(extDir, yname);
        mylog("create disx.txt succeed!" + filex.getAbsolutePath());
        mylog("create disy.txt succeed!" + filey.getAbsolutePath());
        if (!filex.exists()) {
            try {
                filex.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!filey.exists()) {
            try {
                filey.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        //fab.setOnClickListener(new View.OnClickListener() {
        //    @Override
        //    public void onClick(View view) {
        //        Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
        //                .setAction("Action", null).show();
        //    }
        //});

        btnPlayRecord = (Button) findViewById(R.id.btnplayrecord);
        btnStopRecord = (Button) findViewById(R.id.btnstoprecord);
        texDistance_x = (TextView) findViewById(R.id.textView);
        texDistance_y = (TextView) findViewById(R.id.texdistance);
        mytrace = (TraceView) findViewById(R.id.trace);


        soundspeed = 331.3 + 0.606 * temperature;


        for (int i = 0; i < numfreq; i++) {
            wavefreqs[i] = startfreq + i * freqinter;
            wavelength[i] = soundspeed / wavefreqs[i] * 1000;
        }


        disx = 0;
        disy = 250;
        now = 0;
        lastcalibration = 0;

        tracecount = 0;

        mylog("initialization start at time: " + System.currentTimeMillis());
        mylog(initdownconvert(sampleRateInHz, numfreq, wavefreqs));

        mylog("initialization finished at time: " + System.currentTimeMillis());


        //
        btnPlayRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnPlayRecord.setEnabled(false);
                btnStopRecord.setEnabled(true);

                recBufSize = AudioRecord.getMinBufferSize(sampleRateInHz,
                        channelConfig, encodingBitrate);

                mylog("recbuffersize:" + recBufSize);

                playBufSize = AudioTrack.getMinBufferSize(sampleRateInHz,
                        channelConfig, encodingBitrate);

                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        sampleRateInHz, channelConfig, encodingBitrate, recBufSize);


                mylog("channels:" + audioRecord.getChannelConfiguration());

                new ThreadInstantPlay().start();
                new ThreadInstantRecord().start();
                new ThreadSocket().start();
            }
        });
        //
        btnStopRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnPlayRecord.setEnabled(true);
                btnStopRecord.setEnabled(false);
                blnPlayRecord = false;
                isCalibrated = false;
                try {
                    datastream.close();
                    datasocket.close();
                } catch (Exception e) {
                    //TODOL handle this
                }

            }
        });

    }


    private Handler updateviews = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                if (isCalibrated) {
                    texDistance_x.setText(String.format("x=%04.2f", disx / 20) + "cm");
                    texDistance_y.setText(String.format("y=%04.2f", disy / 20) + "cm");
                } else {
                    texDistance_x.setText("Calibrating...");
                    texDistance_y.setText("");
                }
                mylog("count" + tracecount);
                mytrace.setTrace(trace_x, trace_y, tracecount);
                tracecount = 0;
            }
        }


    };

    class ThreadSocket extends Thread {
        public void run() {
            try {
                datasocket = new Socket("192.168.1.10", 12345);//"114.212.85.187"
                mylog("socket connected" + datasocket);
                datastream = datasocket.getOutputStream();
                mylog("socketsream:" + datastream);
                //datastream.write("test".getBytes());

            } catch (Exception e) {
                // TODO: handle this
                mylog("socket error" + e);
            }
        }
    }

    class ThreadInstantPlay extends Thread {
        @Override
        public void run() {
            SoundPlayer Player = new SoundPlayer(sampleRateInHz, numfreq, wavefreqs);
            blnPlayRecord = true;
            Player.play();
            while (blnPlayRecord == true) {
            }
            Player.stop();
        }
    }

    class ThreadInstantRecord extends Thread {

        //private short [] bsRecord = new short[recBufSize];
        //

        @Override
        public void run() {
            short[] bsRecord = new short[recBufSize * 2];
            byte[] networkbuf = new byte[recBufSize * 4];
            int datacount = 0;
            int curpos = 0;
            long starttime, endtime;
            String c_result;

            while (blnPlayRecord == false) {
            }
            audioRecord.startRecording();
            //建立缓冲区写文件
            FileWriter fwx = null, fwy = null;
            try {
                fwx = new FileWriter(filex.getAbsoluteFile());
                fwy = new FileWriter(filey.getAbsoluteFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
            BufferedWriter bwx = new BufferedWriter(fwx);
            BufferedWriter bwy = new BufferedWriter(fwy);

            /*
             *
             */
            while (blnPlayRecord) {
                /*
                 *
                 */

                int line = audioRecord.read(bsRecord, 0, frameSize * 2);
                datacount = datacount + line / 2;
                now = now + 1;

                mylog("recevied data:" + line + " at time" + System.currentTimeMillis());
                if (line >= frameSize) {

                    //get baseband  bsRecord => baseband
                    starttime = System.currentTimeMillis();
                    mylog(getbaseband(bsRecord, baseband, line / 2));
                    endtime = System.currentTimeMillis();
                    mylog("time used for baseband:" + (endtime - starttime));

                    // baseband => baseband_nodc
                    starttime = System.currentTimeMillis();
                    mylog(removedc(baseband, baseband_nodc, dcvalue));
                    endtime = System.currentTimeMillis();
                    mylog("time used for LEVD:" + (endtime - starttime));

                    // baseband_nodc => phasechange, dischange, freqpower
                    starttime = System.currentTimeMillis();
                    mylog(getdistance(baseband_nodc, phasechange, dischange, freqpower));
                    endtime = System.currentTimeMillis();
                    mylog("time used for distance:" + (endtime - starttime));


                    if (!isCalibrated && Math.abs(dischange[0]) < 0.05 && now - lastcalibration > 10) {
                        c_result = calibrate(baseband);
                        mylog(c_result);
                        lastcalibration = now;
                        if (c_result.equals("calibrate OK")) {
                            isCalibrated = true;
                        }
                    }
                    if (isCalibrated) {
                        // baseband_nodc => idftdis  IDFT
                        starttime = System.currentTimeMillis();
                        mylog(getidftdistance(baseband_nodc, idftdis));
                        endtime = System.currentTimeMillis();
                        mylog("time used for idftdistance:" + (endtime - starttime));

                        //keep difference stable;
                        double disdiff, dissum;
                        disdiff = dischange[0] - dischange[1];
                        dissum = dischange[0] + dischange[1];
                        dischangehist = dischangehist * 0.5 + disdiff * 0.5;
                        dischange[0] = (dissum + dischangehist) / 2;
                        dischange[1] = (dissum - dischangehist) / 2;
                        disx = disx + dischange[0];
                        if (disx > 1000)
                            disx = 1000;
                        if (disx < 0)
                            disx = 0;
                        disy = disy + dischange[1];
                        if (disy > 1000)
                            disy = 1000;
                        if (disy < 0)
                            disy = 0;
                        if (Math.abs(dischange[0]) < 0.2 && Math.abs(dischange[1]) < 0.2 && Math.abs(idftdis[0]) > 0.1 && Math.abs(idftdis[1]) > 0.1) {
                            disx = disx * (1 - distrend) + idftdis[0] * distrend;
                            disy = disy * (1 - distrend) + idftdis[1] * distrend;
                        }
                        if (disx < micdis1)
                            disx = micdis1;
                        if (disy < micdis2)
                            disy = micdis2;
                        if (Math.abs(disx - disy) > (micdis1 + micdis2)) {
                            double tempsum = disx + disy;
                            if (disx > disy) {
                                disx = (tempsum + micdis1 + micdis2) / 2;
                                disy = (tempsum - micdis1 - micdis2) / 2;

                            } else {
                                disx = (tempsum - micdis1 - micdis2) / 2;
                                disy = (tempsum + micdis1 + micdis2) / 2;
                            }
                        }
                        trace_x[tracecount] = (int) Math.round((disy * micdis1 * micdis1 - disx * micdis2 * micdis2 + disx * disy * (disy - disx)) / 2 / (disx * micdis2 + disy * micdis1));
                        trace_y[tracecount] = (int) Math.round(Math.sqrt(Math.abs((disx * disx - micdis1 * micdis1) * (disy * disy - micdis2 * micdis2) * ((micdis1 + micdis2) * (micdis1 + micdis2) - (disx - disy) * (disx - disy)))) / 2 / (disx * micdis2 + disy * micdis1));
                        mylog("x=" + trace_x[tracecount] + "y=" + trace_y[tracecount]);
                        mylog("disx=" + disx + " disy=" + disy);
                        //写入disx
                        try {
                            bwx.write(disx + ",");
                            bwy.write(disy + ",");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        tracecount++;
                    }
                    if (Math.abs(displaydis - disx) > 2 || (tracecount > 10)) {
                        Message msg = new Message();
                        msg.what = 0;
                        displaydis = disx;
                        updateviews.sendMessage(msg);
                    }
                    if (!isCalibrated) {
                        Message msg = new Message();
                        msg.what = 0;
                        updateviews.sendMessage(msg);
                    }


                    curpos = curpos + line / 2;
                    if (curpos > coscycle)
                        curpos = curpos - coscycle;
                    if (sendbaseband && datastream != null) {
                        int j = 0;
                        for (int i = 0; i < 2 * numfreq * 2 * frameSize / cicdec; i++) {
                            //sum = sum + bsRecord[i];
                            networkbuf[j++] = (byte) (((short) baseband_nodc[i]) & 0xFF);
                            networkbuf[j++] = (byte) (((short) baseband_nodc[i]) >> 8);
                        }
                        //Log.i("wavedemo", "data sum:" + sum);

                        if (datastream != null) {
                            try {
                                datastream.write(networkbuf, 0, j);
                                mylog("socket write" + j);
                            } catch (Exception e) {
                                // TODO: handle this
                                mylog("socket error" + e);
                            }
                        }
                    }

                    if (sendDatatoMatlab && datastream != null) {
                        int j = 0;
                        int sum = 0;
                        for (int i = 0; i < line; i++) {
                            //sum = sum + bsRecord[i];
                            networkbuf[j++] = (byte) (bsRecord[i] & 0xFF);
                            networkbuf[j++] = (byte) (bsRecord[i] >> 8);
                        }
                        //Log.i("wavedemo", "data sum:" + sum);

                        if (datastream != null) {
                            try {
                                datastream.write(networkbuf, 0, j);
                                mylog("socket write" + j);
                            } catch (Exception e) {
                                // TODO: handle this
                                mylog("socket error" + e);
                            }
                        }
                    }
//                    if(sendDataToFile){
//                        int j = 0;
//                        for (int i = 0; i < line; i++) {
//                            //sum = sum + bsRecord[i];
//                            networkbuf[j++] = (byte) (bsRecord[i] & 0xFF);
//                            networkbuf[j++] = (byte) (bsRecord[i] >> 8);
//                            try {
//                                fileOutputStream.write(networkbuf, 0, j);
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    }

                }
                mylog("time used one loop" + System.currentTimeMillis());


            }
            audioRecord.stop();
            try {
                bwx.close();
                bwy.close();
//                addHeadData();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public void mylog(String information) {
        if (logenabled) {
            Log.i(sysname, information);
        }
    }

    private String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE);
        }
    }

    public byte[] toByteArray(short[] src) {

        int count = src.length;
        byte[] dest = new byte[count << 1];
        for (int i = 0; i < count; i++) {
            dest[i * 2] = (byte) (src[i]);
            dest[i * 2 + 1] = (byte) (src[i] >> 8);
        }

        return dest;
    }

    private void addHeadData() {
        handlerWavFile = new File(Environment.getExternalStorageDirectory(), "audioRecord_handler.wav");
        PcmToWavUtil pcmToWavUtil = new PcmToWavUtil(48000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
//        pcmToWavUtil.pcmToWav(pcmFile.toString(),handlerWavFile.toString());
    }

    // C implementation of down converter

    public native String getbaseband(short[] data, double[] outdata, int numdata);

    //C implementation of LEVD

    public native String removedc(double[] data, double[] data_nodc, short[] outdata);

    //C implementation of distance

    public native String getdistance(double[] data, double[] outdata, double[] distance, double[] freqpower);

    // Initialize C down converter

    public native String initdownconvert(int samplerate, int numfreq, double[] wavfreqs);

    public native String getidftdistance(double[] data, double[] outdata);

    public native String calibrate(double[] data);

    static {
        System.loadLibrary("native-lib");
    }
  /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }*/
}
