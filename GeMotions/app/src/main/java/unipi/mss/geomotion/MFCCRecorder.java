package unipi.mss.geomotion;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;


import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;



public class MFCCRecorder {


    private String TAG = "MFCCRecoder";

    AudioDispatcher dispatcher = null;

    // List of MFCC coefficients
    ArrayList<float[]> mfccList;

    // MFCC Extraction parameters
    final int samplesPerFrame = 2048;
    final int sampleRate = 16000;
    final int amountOfCepstrumCoef = 13;
    float lowerFilterFreq = 133.3334f;
    float upperFilterFreq = ((float)sampleRate)/2f;

    public ArrayList<float[]> getMfccList() {
        return mfccList;
    }

    public MFCCRecorder() {
        Log.d(this.TAG, "MFCC Recorder initialized");
    }

    public void InitAudioDispatcher() {
        Log.d(this.TAG, "Initializing the Tarsos Audio Dispatcher");
        mfccList = new ArrayList<float[]>();
        // telling the dispatcher that we want to have the data coming from the device microphone
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(24000, 16000, 512);
    }


    public void StopAudioDispatcher() {
        float processedSeconds = dispatcher.secondsProcessed();
        Log.d(this.TAG, "Second of audio that have been processed:" + processedSeconds);
        dispatcher.stop();
        normalizeFeatures(mfccList);
    }


    public void startMfccExtraction()
    {


        //MFCC( samplesPerFrame, sampleRate ) //typical samplesperframe are power of 2 & Samples per frame = (sample rate)/FPS
        //Florian suggested to use 16kHz as sample rate and 512 for frame size
        int amountOfMelFilters = 30;
        final MFCC mfccObj = new MFCC(samplesPerFrame, sampleRate, amountOfCepstrumCoef, amountOfMelFilters, lowerFilterFreq, upperFilterFreq );

  		/*AudioProcessors are responsible for actual digital signal processing. AudioProcessors are meant to be chained
  		e.g. execute an effect and then play the sound.
  		The chain of audio processor can be interrupted by returning false in the process methods.
  		*/
        dispatcher.addAudioProcessor( mfccObj);
        //handlePitchDetection();
        dispatcher.addAudioProcessor(new AudioProcessor() {

            @Override
            public void processingFinished() {
                //Notify the AudioProcessor that no more data is available and processing has finished
            }

            @Override
            public boolean process(AudioEvent audioEvent) {

                //process the audio event. do the actual signal processing on an (optionally) overlapping buffer

                //fetchng MFCC array and removing the 0th index because its energy coefficient and florian asked to discard
                float[] mfccOutput = mfccObj.getMFCC();
                mfccOutput = Arrays.copyOfRange(mfccOutput, 1, mfccOutput.length);

                //Storing in global arraylist so that i can easily transform it into csv
                mfccList.add(mfccOutput);
                Log.i("MFCC", String.valueOf(Arrays.toString(mfccOutput)));
                return true;
            }
        });


        //its better to use thread vs asynctask here. ref : http://stackoverflow.com/a/18480297/1016544
        new Thread(dispatcher, "Audio Dispatcher").start();

    }

    public void normalizeFeatures(ArrayList<float[]> features) {
        // Trova il massimo e il minimo valore tra tutte le feature
        float maxVal = Float.MIN_VALUE;
        float minVal = Float.MAX_VALUE;

        for (float[] feature : features) {
            for (float value : feature) {
                maxVal = Math.max(maxVal, value);
                minVal = Math.min(minVal, value);
            }
        }

        // Calcola il range
        float range = maxVal - minVal;

        // Normalizza ogni feature tra -1 e 1
        for (float[] feature : features) {
            for (int i = 0; i < feature.length; i++) {
                feature[i] = (feature[i] - minVal) / range * 2 - 1; // Normalizzazione tra -1 e 1
            }
        }
    }
}
