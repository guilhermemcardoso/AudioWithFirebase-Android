package com.gmcardoso.audiowithfirebase;

import android.*;
import android.Manifest;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.jar.*;

public class MainActivity extends AppCompatActivity {

    private MediaRecorder mRecorder = null;
    private String mFileName = null;
    private static final String LOG_TAG = "Record_log";
    private StorageReference mStorage;
    private DatabaseReference mDatabase;
    private Button recordButton;
    private TextView textViewStatus;
    private EditText editTextAuthor;
    private RecyclerView mRecyclerView;
    private TextView mListaMensagensVazia;
    private List<Message> mMessageList;
    private boolean isPlaying;
    private MediaPlayer mediaPlayer;

    private Message mMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMessage = new Message();
        mMessageList = new ArrayList<>();
        mediaPlayer = null;

        recordButton = (Button) findViewById(R.id.button_record);
        textViewStatus = (TextView) findViewById(R.id.text_view_status);
        editTextAuthor = (EditText) findViewById(R.id.edit_text_author);
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view_message_list);
        mListaMensagensVazia = (TextView) findViewById(R.id.no_messages_text);

        isPlaying = false;

        recordButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    startRecording();
                } else if(event.getAction() == MotionEvent.ACTION_UP) {
                    stopRecording();
                }

                return false;
            }
        });

        mStorage = FirebaseStorage.getInstance().getReference();
        mDatabase = FirebaseDatabase.getInstance().getReference().child("messages");
        downloadAudios();
    }

    private void startRecording() {

        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        File folder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AudioFirebase");
        if(!folder.exists()) {
            folder.mkdir();
        }
        mFileName += "/AudioFirebase/audio.3gp" ;
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }

        mRecorder.start();
        textViewStatus.setText("Recording...");
        textViewStatus.setVisibility(View.VISIBLE);
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        textViewStatus.setText("Uploading audio file...");
        uploadAudio();
        textViewStatus.setText("Audio file uploaded successfully");
    }

    private void uploadAudio() {
        mDatabase = FirebaseDatabase.getInstance().getReference().child("messages").push();
        String idMessage = mDatabase.getKey();

        Calendar rightNow = Calendar.getInstance();

        if(editTextAuthor.getText().toString().equals("")) {
            mMessage.setAuthor("Anonymous");
        } else {
            mMessage.setAuthor(editTextAuthor.getText().toString());
        }

        mMessage.setMessageId(idMessage);
        mMessage.setType("audio");
        mMessage.setDate(String.valueOf(rightNow.getTimeInMillis()));
        mMessage.setText(idMessage + ".3gp");

        //mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/AudioFirebase/audio.3gp";
        mDatabase.setValue(mMessage);

        StorageReference filepath = mStorage.child("Audio").child(mMessage.getText());
        Uri uri = Uri.fromFile(new File(mFileName));
        filepath.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Context context = getApplicationContext();
                CharSequence text = "Arquivo de audio enviado";
                int duration = Toast.LENGTH_SHORT;
                File deleteFile = new File(Environment.getExternalStorageDirectory() + "/AudioFirebase", "audio.3gp");
                deleteFile.delete();
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
                downloadAudios();
            }
        });
    }

    private void downloadAudios() {
        mDatabase = FirebaseDatabase.getInstance().getReference().child("messages");
        FirebaseRecyclerAdapter<Message, MessageHolder> mFirebaseAdapter;
        mFirebaseAdapter = new FirebaseRecyclerAdapter<Message, MessageHolder>(Message.class,
                R.layout.message_list_item, MessageHolder.class, mDatabase) {
            @Override
            protected void populateViewHolder(final MessageHolder viewHolder, final Message model, int position) {

                mListaMensagensVazia.setVisibility(View.GONE);
                mMessageList.add(model);

                StorageReference download = mStorage.child("Audio").child(model.getText());

                File localFile = new File(Environment.getExternalStorageDirectory() + "/AudioFirebase", model.getText());
                if(!localFile.exists()) {
                    //localFile.createNewFile();
                    download.getFile(localFile);
                }

                viewHolder.tvMessageAuthor.setText(model.getAuthor());
                viewHolder.tvMessageDate.setText(model.getDate().toString());

                viewHolder.btnPlay.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(!isPlaying) {
                            viewHolder.btnPlay.setText("Stop");
                            String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AudioFirebase";
                            String fileName = model.getText();

                            path += File.separator + fileName;

                            try {

                                mediaPlayer = new MediaPlayer();
                                mediaPlayer.setDataSource(path);

                                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                    @Override
                                    public void onCompletion(MediaPlayer mp) {
                                        isPlaying = false;
                                        viewHolder.btnPlay.setText("Play");
                                    }
                                });
                                mediaPlayer.prepare();
                                mediaPlayer.start();
                                isPlaying = true;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        } else {
                            if(mediaPlayer != null && isPlaying) {
                                mediaPlayer.stop();
                                mediaPlayer.release();
                                mediaPlayer = null;
                                isPlaying = false;
                                viewHolder.btnPlay.setText("Play");
                            }
                        }


                    }
                });
            }
        };

        mRecyclerView.setAdapter(mFirebaseAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    public static class MessageHolder extends RecyclerView.ViewHolder {

        public TextView tvMessageAuthor;
        public TextView tvMessageDate;
        public Button btnPlay;

        public MessageHolder(View itemView) {
            super(itemView);

            tvMessageAuthor = (TextView) itemView.findViewById(R.id.text_view_message_author);
            tvMessageDate = (TextView) itemView.findViewById(R.id.text_view_message_date);
            btnPlay = (Button) itemView.findViewById(R.id.button_play);
        }
    }
}


