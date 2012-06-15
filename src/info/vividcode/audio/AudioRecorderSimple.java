package info.vividcode.audio;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public class AudioRecorderSimple extends Thread {

    /**
     * ファイルに書き出すための worker
     */
    private static class AudioFileWritingWorker extends Thread {
        private boolean mToBeEnd;
        private BlockingQueue<RecedAudioData> mQueue;
        public AudioFileWritingWorker( BlockingQueue<RecedAudioData> queue ) {
            mQueue = queue;
            mToBeEnd = false;
        }
        public void toBeEnd() {
            mToBeEnd = true;
            System.out.println( "toBeEnd!!!" );
        }
        /**
         * ファイル名を表す String オブジェクトを作成する
         */
        private String createFileName( Calendar calendar ) {
            String fileName = "test";
            //Calendar calendar = Calendar.getInstance( TimeZone.getDefault() );
            String time = String.format( "%04d-%02d-%02d_%02d%02d%02d",
                    calendar.get( Calendar.YEAR ),
                    calendar.get( Calendar.MONTH ) + 1,
                    calendar.get( Calendar.DAY_OF_MONTH ),
                    calendar.get( Calendar.HOUR ),
                    calendar.get( Calendar.MINUTE ),
                    calendar.get( Calendar.SECOND ) );
            fileName += "_" + time + ".wav";
            return fileName;
        }
        private void putFileHeader( BufferedOutputStream bos ) throws IOException {
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
        }
        @Override
        public void run() {
            byte[] buf = new byte[4096];
            Calendar lastPolled = Calendar.getInstance();
            while ( true ) {
                // ファイルの変数
                File file = null;
                BufferedOutputStream bos = null;
                try {
                    long dataSize = 0; // 波形データのバイト数
                    while ( true ) {
                        RecedAudioData data = null;
                        try {
                            data = mQueue.poll( 500, TimeUnit.MILLISECONDS );
                        } catch ( InterruptedException err ) {
                            if ( ! mToBeEnd ) err.printStackTrace();
                            mToBeEnd = true; // これでいい?
                        }
                        if ( data == null ) {
                            // 最期に書き込んだデータの日時などを見て, 時間が過ぎまくっているなら例外出して終わる
                            if ( Calendar.getInstance().getTimeInMillis() - lastPolled.getTimeInMillis() > 5000 ) {
                                break;
                            }
                            // 特に問題ないなら次へ
                            if ( mToBeEnd ) break;
                            else            continue;
                        }
                        lastPolled = data.time;
                        // ファイルが存在しないならファイルを作成
                        if ( file == null ) {
                            System.out.println( "ファイル作成" );
                            // TODO 時刻は取得時の時刻にする
                            file = new File( createFileName( Calendar.getInstance( TimeZone.getDefault() ) ) );
                            bos = new BufferedOutputStream( new FileOutputStream( file ) );
                            putFileHeader( bos );
                        }
                        // 波形データの出力
                        while( true ) {
                            int readLength = data.stream.read( buf );
                            if( readLength <= 0 ) {
                                break;
                            }
                            dataSize += readLength;
                            bos.write( buf, 0, readLength );
                        }
                        //AudioSystem.write( mAudioInputStream, mTargetType, mOutputFile );
                        // ファイルに書き込む処理
                        if ( mToBeEnd ) break;
                        // 指定の条件 (ファイルサイズとか) に達した場合は break する
                        if( dataSize > 102400000 ) break;
                    }

                    // ファイルサイズの書き込み
                    RandomAccessFile raf = null;
                    try {
                        raf = new RandomAccessFile( file, "rw" );
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
                    } finally {
                        if ( raf != null ) raf.close();
                    }
                } catch ( IOException err ) {
                    err.printStackTrace();
                } finally {
                    try {
                        if ( bos != null ) bos.close();
                    } catch ( IOException err ) {
                        err.printStackTrace();
                    }
                }
                if ( mToBeEnd ) break;
            }
        }
    }

    /**
     * データを格納するためのデータ構造
     */
    private static class RecedAudioData {
        public AudioInputStream stream;
        public Calendar time;
        public RecedAudioData( AudioInputStream ais, Calendar cal ) {
            stream = ais;
            time = cal;
        }
    }

    private TargetDataLine mLine;
    //private AudioFileFormat.Type mTargetType;
    private AudioInputStream mAudioInputStream;
    //private File mOutputFile;
    //private GraphPanel mGraphPanel;
    
    // 44.1 kHz, 16 bit, ステレオの設定でオーディオ形式を生成します
    AudioFormat AUDIO_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, // Audio Encoding 方式
            44100.0F, // 1 秒あたりのサンプル数
            16, //16, // 各サンプルのビット数
            2, //2,  // チャンネル数
            4, //4,  // フレームあたりのバイト数
            44100.0F, // frame レート
            false );  // big エンディアンかどうか

    public AudioRecorderSimple() {
        try {

            // データラインの情報オブジェクトを生成します
            //DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            // 指定されたデータライン情報に一致するラインを取得します
            //mLine = (TargetDataLine) AudioSystem.getLine(info);
            mLine = getTargetDataLine();
            // 指定されたオーディオ形式でラインを開きます
            mLine.open( AUDIO_FORMAT );

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
        BlockingQueue<RecedAudioData> queue = new LinkedBlockingQueue<AudioRecorderSimple.RecedAudioData>();
        AudioFileWritingWorker worker = new AudioFileWritingWorker( queue );
        worker.start();
        while ( true ) {
            // フレームごとに区切るので, 2 byte * 2 chnnel ということで 4 バイト毎で渡す
            int readLength = -1;
            byte[] buf = new byte[4096];
            try {
                readLength = mAudioInputStream.read( buf ); // 常にフレームサイズの積分数を読み込む
            } catch ( IOException err ) {
                err.printStackTrace();
            }
            if ( readLength <= 0 ) break;
            // 今回の周のもの
            InputStream stream = new ByteArrayInputStream( buf, 0, readLength );
            AudioInputStream ais = new AudioInputStream( stream, AUDIO_FORMAT, readLength / 4 );
            Calendar calendar = Calendar.getInstance( TimeZone.getDefault() );
            try {
                queue.put( new RecedAudioData( ais, calendar ) );
            } catch ( InterruptedException err ) {
                // TODO Auto-generated catch block
                err.printStackTrace();
            }
        }
        worker.toBeEnd();
    }

    private TargetDataLine getTargetDataLine() {
        return getTargetDataLine( null );
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
        if( aMixerName != null && targetDataLineHash.containsKey( aMixerName ) ) {
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
