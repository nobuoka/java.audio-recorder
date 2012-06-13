package info.vividcode.audio;

import java.io.IOException;
import java.util.ArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * 複数ある Mixer の現在の入力状態を表示し, どの Mixer を使うのかを
 * ユーザーに選択させるためのクラス. 記述中
 */
public class AudioLineSelecter {

    // 44.1 kHz, 16 bit, ステレオの設定でオーディオ形式を生成します
    private static AudioFormat audioFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, // Audio Encoding 方式
            44100.0F, // 1 秒あたりのサンプル数
            8, //16, // 各サンプルのビット数
            1, //2,  // チャンネル数
            1, //4,  // フレームあたりのバイト数
            44100.0F, // frame レート
            false );  // big エンディアンかどうか

    private static class AudioSignalDrainer extends Thread {
        private TargetDataLine mTdLine;
        public AudioSignalDrainer( TargetDataLine tdLine ) {
            mTdLine = tdLine;
        }
        public void startD() {
            try {
                mTdLine.open( audioFormat );
            } catch (LineUnavailableException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mTdLine.start();
            super.start();
        }
        public void stopD() {
            mTdLine.stop();
            mTdLine.close();
        }
        @Override
        public void run() {
            AudioInputStream ais = new AudioInputStream( mTdLine );
            //ais = AudioSystem.getAudioInputStream( audioFormat, ais );
            double sum = 0.0;
            try {
                byte[] buf = new byte[4096];
                boolean endFlag = false;
                long dataSize = 0; // 波形データのバイト数
                while( ! endFlag ) {
                    // 波形データの出力
                    int readLength = ais.read( buf );
                    if( readLength <= 0 ) {
                        endFlag = true;
                        break;
                    } else {
                        for ( int i = 0; i < readLength; ++ i ) {
                            sum += buf[i] * buf[i];
                        }
                    }
                    dataSize += readLength;
                }
                System.out.println( sum / dataSize + " " + dataSize );
            } catch ( IOException err ) {
                err.printStackTrace();
            } finally {
                try {
                    ais.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main( String[] args ) {
        Mixer.Info[] mixerInfoList = AudioSystem.getMixerInfo();
        ArrayList<Mixer.Info> filteredMixerInfoList = new ArrayList<Mixer.Info>();
        for( Mixer.Info info : mixerInfoList ) {
            String name = info.getName();
            System.out.println( name + ": " + info.getDescription() );
            Mixer mixer = AudioSystem.getMixer(info);
            DataLine.Info targetDataLineInfo = new DataLine.Info( TargetDataLine.class, audioFormat );
            if ( mixer.isLineSupported( targetDataLineInfo ) ) {
                filteredMixerInfoList.add( info );
            }
        }

        for ( Mixer.Info mixerInfo : filteredMixerInfoList ) {
            try {
                TargetDataLine tdLine = AudioSystem.getTargetDataLine( audioFormat, mixerInfo );
                AudioSignalDrainer asd = new AudioSignalDrainer( tdLine );
                System.out.println( mixerInfo.getName() + " " + tdLine.getLevel() );
                asd.startD();
                try {
                    Thread.sleep( 1000 );
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                asd.stopD();
            } catch (LineUnavailableException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }

    }

}
