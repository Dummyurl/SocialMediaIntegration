package com.decimalcorp.aditya.socialmediaintegration;

import android.app.ProgressDialog;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.Profile;
import com.facebook.ProfileTracker;
import com.facebook.login.LoginManager;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.linkedin.platform.APIHelper;
import com.linkedin.platform.LISessionManager;
import com.linkedin.platform.errors.LIApiError;
import com.linkedin.platform.listeners.ApiListener;
import com.linkedin.platform.listeners.ApiResponse;
import com.squareup.picasso.Picasso;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.models.User;

import org.json.JSONObject;

import retrofit2.Call;

public class UserProfile extends AppCompatActivity {

    ProgressDialog progress;
    ImageView profile_pic;
    TextView user_name,user_email;
    AccessToken accessToken;
    ProfileTracker profileTracker;
    GoogleApiClient mGoogleApiClient;
    GoogleSignInAccount mGoogleSignInAccount;

    private static final String host = "api.linkedin.com";
    private static final String topCardUrl = "https://" + host + "/v1/people/~:" +
            "(email-address,formatted-name,phone-numbers,public-profile-url,picture-url,picture-urls::(original))";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        accessToken = AccessToken.getCurrentAccessToken();
        final GoogleSignInAccount acct = GoogleSignIn.getLastSignedInAccount(getBaseContext());

        profile_pic = (ImageView) findViewById(R.id.u_pic);
        user_email = (TextView) findViewById(R.id.u_email);
        user_name = (TextView) findViewById(R.id.u_name);

        progress= new ProgressDialog(this);
        progress.setMessage("Retrieve data...");
        progress.setCanceledOnTouchOutside(false);
        progress.show();

        if(accessToken != null && !accessToken.isExpired()){
            Toast.makeText(this,"Facebook Logged In",Toast.LENGTH_SHORT).show();
            final Profile profile = Profile.getCurrentProfile();
            profileTracker = new ProfileTracker() {
                @Override
                protected void onCurrentProfileChanged(Profile oldProfile, Profile currentProfile) {
                    this.stopTracking();
                    progress.dismiss();

                    setUserProfile(currentProfile);
                }
            };
                profileTracker.startTracking();

            if(profile != null)
                setUserProfile(profile);

        }
        else if(TwitterCore.getInstance().getSessionManager().getActiveSession() != null){
            Toast.makeText(this,"Twitter Logged In",Toast.LENGTH_SHORT).show();

            getTwitterUserData();
        }else if(acct!=null){
            Toast.makeText(this,"Google Logged In",Toast.LENGTH_SHORT).show();
            setUserProfile(acct);
        }
        else{
            Toast.makeText(this,"LinkedIn Logged In",Toast.LENGTH_SHORT).show();
            getLIUserData();

        }
        Button logout = (Button) findViewById(R.id.signout);
        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(profileTracker != null)
                profileTracker.stopTracking();

                Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if(!status.isSuccess()){
                            Toast.makeText(getBaseContext(),"Error signing out",Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                mGoogleApiClient.disconnect();

                TwitterCore.getInstance().getSessionManager().clearActiveSession();
                LISessionManager.getInstance(getApplicationContext()).clearSession();
                LoginManager.getInstance().logOut();
                Intent intent = new Intent(UserProfile.this, LoginActivity.class);
                startActivity(intent);
            }
        });

    }

    @Override
    protected void onStart() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
        mGoogleApiClient.connect();
        super.onStart();
    }

    public void getLIUserData(){
        APIHelper apiHelper = APIHelper.getInstance(getApplicationContext());
        apiHelper.getRequest(UserProfile.this, topCardUrl, new ApiListener() {
            @Override
            public void onApiSuccess(ApiResponse result) {
                try {

                    setUserProfile(result.getResponseDataAsJson());
                    progress.dismiss();

                } catch (Exception e){
                    e.printStackTrace();
                }

            }

            @Override
            public void onApiError(LIApiError error) {
                // ((TextView) findViewById(R.id.error)).setText(error.toString());

            }
        });
    }


    public  void  setUserProfile(JSONObject response){

        try {

            user_email.setText(response.get("emailAddress").toString());
            user_name.setText(response.get("formattedName").toString());

            Picasso.get().load(response.getString("pictureUrl")).into(profile_pic);

        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void setUserProfile(Profile user){
        try {
            String x= user.getLinkUri().toString();
            user_email.setText(x);
            user_name.setText(user.getName());

            //Toast.makeText(getBaseContext(),"name =  "+x,Toast.LENGTH_SHORT).show();

            Picasso.get().load(user.getProfilePictureUri(100,100)).into(profile_pic);

        } catch (Exception e){
            e.printStackTrace();
            Toast.makeText(getBaseContext(),"ERROR =  "+user.getName(),Toast.LENGTH_SHORT).show();

        }
    }

    void setUserProfile(GoogleSignInAccount account){
        try{
            user_name.setText(account.getDisplayName());
            user_email.setText(account.getEmail());
            Picasso.get().load(account.getPhotoUrl()).into(profile_pic);
            progress.dismiss();
        }
        catch (Exception e){
            Toast.makeText(getBaseContext(),"Oops! Try Again \n" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    void getTwitterUserData(){
        TwitterSession session = TwitterCore.getInstance().getSessionManager().getActiveSession();
        Call<User> user = TwitterCore.getInstance().getApiClient().getAccountService().verifyCredentials(false, false, true);
        user.enqueue(new Callback<User>() {
            @Override
            public void success(Result<User> userResult) {
                String name = userResult.data.name;
                String email = userResult.data.email;
                String photoUrlBiggerSize   = userResult.data.profileImageUrl.replace("_normal", "_bigger");

                // _normal (48x48px) | _bigger (73x73px) | _mini (24x24px)
                //String photoUrlNormalSize   = userResult.data.profileImageUrl;
                //String photoUrlMiniSize     = userResult.data.profileImageUrl.replace("_normal", "_mini");
                //String photoUrlOriginalSize = userResult.data.profileImageUrl.replace("_normal", "");

                user_email.setText(email);
                user_name.setText(name);
                Picasso.get().load(photoUrlBiggerSize).into(profile_pic);
                progress.dismiss();
            }

            @Override
            public void failure(TwitterException exc) {
                Log.d("TwitterKit", "Verify Credentials Failure", exc);
            }
        });
    }
}
