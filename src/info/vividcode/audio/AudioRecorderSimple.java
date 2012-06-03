package info.vividcode.audio;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.TimeZone;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public class AudioRecorderSimple extends Thread {

    private TargetDataLine mLine;
    //private AudioFileFormat.Type mTargetType;
    private AudioInputStream mAudioInputStream;
    //private File mOutputFile;
    //private GraphPanel mGraphPanel;

    public AudioRecorderSimple() {
        try {
            // 44.1 kHz, 16 bit, ステレオの設定でオーディオ形式を生成します
            AudioFormat audioFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, // Audio Encoding 方式
                    44100.0F, // 1 秒あたりのサンプル数
                    16, //16, // 各サンプルのビット数
                    2, //2,  // チャンネル数
                    4, //4,  // フレームあたりのバイト数
                    44100.0F, // frame レート
                    false );  // big エンディアンかどうか

            // データラインの情報オブジェクトを生成します
            //DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            // 指定されたデータライン情報に一致するラインを取得します
            //mLine = (TargetDataLine) AudioSystem.getLine(info);
            mLine = getTargetDataLine( "SE-U55GX Audio" );
            // 指定されたオーディオ形式でラインを開きます
            mLine.open( audioFormat );

            // 書き込むオーディオファイルの種類を設定します
            //mTargetType = AudioFileFormat.Type.WAVE;
            // 録音するインスタンスを生成
            //App recorder = new HelloWorldRecorder(targetDataLine,targetType,outputFile);
            mAudioInputStream = new AudioInputStream(mLine);
            //mOutputFile = aOutputFile;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void startRecording() {
        mLine.start();
        super.start();
    }

    public void stopRecording() {
        mLine.stop();
        mLine.close();
    }

    @Override
    public void run() {
        try {
            byte[] buf = new byte[4096];
            boolean endFlag = false;
            while( ! endFlag ) {
                String fileName = "test";
                Calendar calendar = Calendar.getInstance( TimeZone.getDefault() );
                String time = String.format( "%04d-%02d-%02d_%02d%02d%02d",
                        calendar.get( Calendar.YEAR ),
                        calendar.get( Calendar.MONTH ) + 1,
                        calendar.get( Calendar.DAY_OF_MONTH ),
                        calendar.get( Calendar.HOUR ),
                        calendar.get( Calendar.MINUTE ),
                        calendar.get( Calendar.SECOND ) );

                fileName += "_" + time + ".wav";
                File file = new File( fileName );
                BufferedOutputStream bos = 
                    new BufferedOutputStream( new FileOutputStream( file ) );
                // ヘッダーの定義
                byte[] header = {
                        0x52, 0x49, 0x46, 0x46, // "RIFF
                        0,0,0,0, // ファイルサイズ [byte] - 8. あとから記述
                        0x57, 0x41, 0x56, 0x45, // "WAVE"
                        // ここから fmt チャンク
                        0x66, 0x6D, 0x74, 0x20, // "fmt "
                        0x10, 0x00, 0x00, 0x00, // fmt チャンクのバイト数
                        0x01, 0x00, // フォーマット ID. リニア PCM
                        0x02, 0x00, // チャンネル数. 2
                        0x44,  -84, 0x00, 0x00, // サンプリングレート. 44.1 kHz
                        0x10, (byte)0xB1, 0x02, 0x00, // データ速度 Byte per sec. 176,400
                        0x04, 0x00, // ブロックサイズ
                        0x10, 0x00, // サンプルあたりのビット数
                        // ここから data チャンク
                        0x64, 0x61, 0x74, 0x61, // "data"
                        0,0,0,0 // 波形データのバイト数
                        };
                // ヘッダーの出力
                bos.write( header );
                // 波形データの出力
                long dataSize = 0; // 波形データのバイト数
                while( true ) {
                    int readLength = mAudioInputStream.read( buf );
                    System.out.println( readLength );
                    if( readLength <= 0 ) {
                        endFlag = true;
                        break;
                    }
                    dataSize += readLength;
                    /*
                    double[] signalArray = new double[buf.length/2];
                    for( int i = 0; i < readLength; i++ ) {
                        if( i % 2 == 0 ) {
                            signalArray[i/2] = (double)buf[i] / 0xFFFF + 0.5;
                        } else {
                            signalArray[i/2] = (double)buf[i] * 0x100  / 0xFFFF + 0.5;
                        }
                    }
                    /*for( int i = readLength; i < buf.length; i++ ) {
                        signalArray[i/2] = 0.0;
                    }*/
                    //mGraphPanel.setSignal( signalArray );
                    bos.write( buf, 0, readLength );
                    if( dataSize > 102400000 ) {
                        break;
                    }
                }
                //AudioSystem.write( mAudioInputStream, mTargetType, mOutputFile );
                bos.close();

                // ファイルサイズの書き込み
                RandomAccessFile raf = new RandomAccessFile( file, "rw" );
                byte[] dataSizeBytes = new byte[4];
                // 40 のところに dataSize
                raf.seek(40);
                dataSizeBytes[0] = (byte)( dataSize & 0xFF );
                dataSizeBytes[1] = (byte)((dataSize >> 8)  & 0xFF );
                dataSizeBytes[2] = (byte)((dataSize >> 16) & 0xFF );
                dataSizeBytes[3] = (byte)((dataSize >> 24) & 0xFF );
                raf.write(dataSizeBytes);
                // 4 のところに dataSize + 36
                raf.seek(4);
                dataSize += 36;
                dataSizeBytes[0] = (byte)( dataSize & 0xFF );
                dataSizeBytes[1] = (byte)((dataSize >> 8)  & 0xFF );
                dataSizeBytes[2] = (byte)((dataSize >> 16) & 0xFF );
                dataSizeBytes[3] = (byte)((dataSize >> 24) & 0xFF );
                raf.write(dataSizeBytes);
                raf.close();
            }
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }
    
    private static void test() {
        try {
            String fileName = "test";
            Calendar calendar = Calendar.getInstance( TimeZone.getDefault() );
            String time = String.format( "%04d-%02d-%02d_%02d%02d%02d",
                    calendar.get( Calendar.YEAR ),
                    calendar.get( Calendar.MONTH ) + 1,
                    calendar.get( Calendar.DAY_OF_MONTH ),
                    calendar.get( Calendar.HOUR ),
                    calendar.get( Calendar.MINUTE ),
                    calendar.get( Calendar.SECOND ) );
            fileName += "_" + /*time +*/ ".wav";
            File file = new File( fileName );
            BufferedOutputStream bos;
            bos = new BufferedOutputStream( new FileOutputStream( file ) );
            // ヘッダーの定義
            byte[] header = {
                    0x52, 0x49, 0x46, 0x46, // "RIFF
                    0,0,0,0, // ファイルサイズ [byte] - 8. あとから記述
                    0x57, 0x41, 0x56, 0x45, // "WAVE"
                    // ここから fmt チャンク
                    0x66, 0x6D, 0x74, 0x20, // "fmt "
                    0x10, 0x00, 0x00, 0x00, // fmt チャンクのバイト数
                    0x01, 0x00, // フォーマット ID. リニア PCM
                    0x02, 0x00, // チャンネル数. 2
                    0x44,  -84, 0x00, 0x00, // サンプリングレート. 44.1 kHz
                    0x10, (byte)0xB1, 0x02, 0x00, // データ速度 Byte per sec. 176,400
                    0x04, 0x00, // ブロックサイズ
                    0x10, 0x00, // サンプルあたりのビット数 (16 bit)
                    // ここから data チャンク
                    0x64, 0x61, 0x74, 0x61, // "data"
                    0,0,0,0 // 波形データのバイト数
                };
            // ヘッダーの出力
            bos.write( header );
            // 波形データの出力
            long dataSize = 0; // 波形データのバイト数
System.out.println( "here1" );
            byte[] buf = new byte[4096];
            for ( int t = 0; t < 441000; ) {
                for ( int j = 0; j < buf.length; ++ t, j += 4 ) {
                    double time_s = (double)t / 44100;
                    double freq;
                    if ( time_s < 1 ) {
                        freq = 110.0;// + Math.sin( 2 * Math.PI * 20 * t / 44100 );
                    } else if ( time_s < 2 ) {
                        freq = 220.0;
                    } else {
                        freq = 440.0;
                    }
                    double rawVal = Math.sin( 2 * Math.PI * freq /* [Hz] */ * t /* [t_unit] */ / 44100 /* [t_unit/s] */ );
                    // rawVal は -1.0 .. 1.0 の値
                    short val = (short)( ( rawVal ) * 0x6FFF );
                    buf[j+0] = (byte)( val & 0xFF );
                    buf[j+2] = (byte)( val & 0xFF );
                    buf[j+1] = (byte)( ( val >> 8 ) & 0xFF );
                    buf[j+3] = (byte)( ( val >> 8 ) & 0xFF );
                    dataSize += 4;
                }
                bos.write( buf, 0, buf.length );
            }
System.out.println( "here2" );

            // ファイルサイズの書き込み
            RandomAccessFile raf = new RandomAccessFile( file, "rw" );
            byte[] dataSizeBytes = new byte[4];
            // 40 のところに dataSize
            raf.seek(40);
            for ( int i = 0; i < 4; ++ i ) // int -> bytes (little endian)
                dataSizeBytes[i] = (byte)( (dataSize>>(i*8)) & 0xFF );
            raf.write(dataSizeBytes);
            // 4 のところに dataSize + 36
            raf.seek(4);
            dataSize += 36;
            for ( int i = 0; i < 4; ++ i ) // int -> bytes (little endian)
                dataSizeBytes[i] = (byte)( (dataSize>>(i*8)) & 0xFF );
            raf.write(dataSizeBytes);
            raf.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void test2() {
        try{
           byte[] wave_data= new byte[44100*4];
           double L1      = 44100.0/440.0;
           double L2      = 44100.0/455.0;
           for ( int t = 0, i = 0; i < wave_data.length; ++ t, i += 4 ) {
               byte[] buf = wave_data;
               double freq = 220;
               double rawVal = Math.sin( 2 * Math.PI * freq /* [Hz] */ * t /* [t_unit] */ / 44100 /* [t_unit/s] */ );
               // rawVal は -1.0 .. 1.0 の値
               short val = (short)( ( rawVal * 0x7FFF ) );
               buf[i+0] = (byte)( val & 0xFF );
               buf[i+2] = (byte)( val & 0xFF );
               buf[i+1] = (byte)( ( val >> 8 ) & 0xFF );
               buf[i+3] = (byte)( ( val >> 8 ) & 0xFF );
               //wave_data[i] = (byte)(55*Math.sin((i/L1)*Math.PI*2));
               //wave_data[i]+= (byte)(55*Math.sin((i/L2)*Math.PI*2));
           }

           AudioFormat format = new AudioFormat( 44100, 16, 2, true, false );
           AudioInputStream ais = new AudioInputStream(
                      new ByteArrayInputStream(wave_data)
                     ,format
                     ,wave_data.length / 4 );
           AudioSystem.write(
                      ais
                     ,AudioFileFormat.Type.WAVE
                     ,new File("test07.wav"));
           }
        catch(Exception e){e.printStackTrace(System.err);}
    }

    private TargetDataLine getTargetDataLine( String aMixerName ) {
        Hashtable<String,TargetDataLine> targetDataLineHash = new Hashtable<String,TargetDataLine>();
        Mixer.Info[] mixerInfoList = AudioSystem.getMixerInfo();
        for( Mixer.Info info : mixerInfoList ) {
            //String name = info.getName();
            //System.out.println( name );
            Mixer mixer = AudioSystem.getMixer(info);
            //System.out.println( mixer );
            //for( Line.Info i : mixer.getSourceLineInfo() ) {
            //  System.out.println( "- " + i ); // output
            //}
            for( Line.Info i : mixer.getTargetLineInfo() ) {
                //System.out.println( "+ " + i ); // input
                try {
                    Line line = mixer.getLine(i);
                    if( line instanceof TargetDataLine ) {
                        //System.out.println( "\tOK" ); // input
                        targetDataLineHash.put( info.getName(), (TargetDataLine)line );
                    }
                } catch ( LineUnavailableException e ) {
                    e.printStackTrace();
                }
            }
        }

        TargetDataLine line = null;
        if( targetDataLineHash.containsKey( aMixerName ) ) {
            line = targetDataLineHash.get( aMixerName );
        } else {
            System.out.println( "使用可能なデバイス一覧" );
            Object[] mixerNames = targetDataLineHash.keySet().toArray();
            for( int i = 0; i < mixerNames.length; i++ ) {
                System.out.println( " " + i + ". " + mixerNames[i] );
            }
            System.out.print( "使用するデバイスの番号を入力してください: " );
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            int number = 0;
            try {
                do {
                    String n = br.readLine();
                    number = Integer.parseInt(n);
                    if( number < 0 || mixerNames.length <= number ) {
                        System.out.println( "入力された値の範囲が無効です. 再度入力してください: " );
                    } else {
                        break;
                    }
                } while(true);
            } catch( Exception e ) {
                System.err.println( "エラーが発生したので 0 番に設定します." );
                number = 0;
            }
            line = targetDataLineHash.get( mixerNames[number] );
        }
        return line;
    }

    //public static void record( GraphPanel aGraphPanel ) {
    public static void main( String[] argv ) {

        System.out.println( "Hello World!" );
        //File outputFile = new File("test.wav");
        //Recorder app = new Recorder( aGraphPanel );
        AudioRecorderSimple app = new AudioRecorderSimple();

        Calendar startTime = Calendar.getInstance( TimeZone.getTimeZone( "Asia/Tokyo" ) );
        Calendar endTime = Calendar.getInstance( TimeZone.getTimeZone( "Asia/Tokyo" ) );
        startTime.set( 2010, 7 - 1, 5, 9, 18, 0 );
        //endTime.set  ( 2011, 9 - 1, 17, 1,  20, 0 );
        endTime.add( Calendar.SECOND, 10 );
        System.out.println( "録音開始まで待機します..." );
        while( Calendar.getInstance().before(startTime) ) {
            try {
                Thread.sleep( 200 );
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println( "録音を開始します..." );
        app.startRecording();
        while( Calendar.getInstance().before(endTime) ) {
            try {
                Thread.sleep( 200 );
            } catch ( InterruptedException e ) {
                e.printStackTrace();
            }
        }
        System.out.println( "録音を終了します..." );
        app.stopRecording();

    }
}