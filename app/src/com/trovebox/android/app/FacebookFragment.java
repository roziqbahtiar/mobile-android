
package com.trovebox.android.app;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import org.holoeverywhere.LayoutInflater;
import org.holoeverywhere.app.Activity;
import org.holoeverywhere.app.Dialog;
import org.holoeverywhere.widget.ProgressBar;
import org.json.JSONObject;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.facebook.android.Facebook;
import com.trovebox.android.app.facebook.FacebookProvider;
import com.trovebox.android.app.facebook.FacebookUtils;
import com.trovebox.android.common.fragment.common.CommonStyledDialogFragment;
import com.trovebox.android.common.model.Photo;
import com.trovebox.android.common.model.utils.PhotoUtils;
import com.trovebox.android.common.net.ReturnSizes;
import com.trovebox.android.common.util.CommonUtils;
import com.trovebox.android.common.util.GuiUtils;
import com.trovebox.android.common.util.LoadingControl;
import com.trovebox.android.common.util.LoadingControlWithCounter;
import com.trovebox.android.common.util.RunnableWithParameter;
import com.trovebox.android.common.util.SimpleAsyncTaskEx;
import com.trovebox.android.common.util.TrackerUtils;
import com.trovebox.android.common.util.concurrent.AsyncTaskEx;

/**
 * @author Eugene Popovich
 */
public class FacebookFragment extends CommonStyledDialogFragment
{
    public static final String TAG = FacebookFragment.class.getSimpleName();
    static final String PHOTO = "FacebookFragmentPhoto";

    Photo photo;

    private EditText messageEt;
    private LoadingControl loadingControl;

    private Button sendButton;

    public static ReturnSizes thumbSize = new ReturnSizes(100, 100);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.fragment_facebook, container);
        if (savedInstanceState != null)
        {
            photo = savedInstanceState.getParcelable(PHOTO);
        }
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        init(view);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(PHOTO, photo);
    }

    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
        loadingControl = ((FacebookLoadingControlAccessor) activity).getFacebookLoadingControl();
    }

    public void setPhoto(Photo photo)
    {
        this.photo = photo;
    }

    void init(View view)
    {
        try
        {
            new ShowCurrentlyLoggedInUserTask(view).execute();
            messageEt = (EditText) view.findViewById(R.id.message);
            messageEt.setText(null);
            Button logOutButton = (Button) view.findViewById(R.id.logoutBtn);
            logOutButton.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    TrackerUtils.trackButtonClickEvent("logoutBtn", FacebookFragment.this);
                    performFacebookLogout();
                }

            });
            sendButton = (Button) view.findViewById(R.id.sendBtn);
            sendButton.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    TrackerUtils.trackButtonClickEvent("sendBtn", FacebookFragment.this);
                    if (GuiUtils.checkLoggedInAndOnline())
                    {
                        postPhoto();
                    }
                }
            });
            {
                sendButton.setEnabled(false);
                PhotoUtils.validateShareTokenExistsAsyncAndRunAsync(photo,
                        new RunnableWithParameter<Photo>() {

                            @Override
                            public void run(Photo parameter) {
                                sendButton.setEnabled(true);
                            }
                        },

                        new Runnable() {

                            @Override
                            public void run() {
                                sendButton.setEnabled(false);
                            }
                        },
                        new FBLoadingControl(view));
            }
        } catch (Exception ex)
        {
            GuiUtils.error(TAG, R.string.errorCouldNotInitFacebookFragment, ex,
                    getActivity());
            dismissAllowingStateLoss();
        }
    }

    protected void postPhoto()
    {
        RunnableWithParameter<Photo> runnable = new RunnableWithParameter<Photo>() {

            @Override
            public void run(Photo photo) {
                new PostPhotoTask(photo).execute();
            }
        };
        PhotoUtils.validateUrlForSizeExistAsyncAndRun(photo, thumbSize, runnable, null,
                loadingControl);
    }

    private void performFacebookLogout()
    {
        FacebookUtils.logoutRequest(getSupportActivity());
        dismissAllowingStateLoss();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Dialog result = super.onCreateDialog(savedInstanceState);
        result.setTitle(R.string.share_facebook_dialog_title);
        return result;
    }

    private class FBLoadingControl extends LoadingControlWithCounter
    {
        ProgressBar progressBar;
        EditText editText;

        FBLoadingControl(View view)
        {
            progressBar = (ProgressBar) view.findViewById(R.id.progressBar2);
            editText = (EditText) view.findViewById(R.id.message);
        }

        @Override
        public void stopLoadingEx() {
            editText.setFocusable(true);
            editText.setFocusableInTouchMode(true);
            progressBar.setVisibility(View.GONE);
        }

        @Override
        public void startLoadingEx() {
            progressBar.setVisibility(View.VISIBLE);
            editText.setFocusable(false);
            editText.setFocusableInTouchMode(false);
        }
    }

    private class ShowCurrentlyLoggedInUserTask extends
            SimpleAsyncTaskEx
    {
        TextView loggedInAsText;
        String name;
        Context activity = getActivity();

        ShowCurrentlyLoggedInUserTask(final View view)
        {
            super(new LoadingControlWithCounter() {

                ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.progressBar);

                @Override
                public void stopLoadingEx() {
                    progressBar.setVisibility(View.GONE);
                }

                @Override
                public void startLoadingEx() {
                    progressBar.setVisibility(View.VISIBLE);
                }
            });
            loggedInAsText = (TextView) view
                    .findViewById(R.id.loggedInAs);
        }

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            loggedInAsText.setText(null);
        }

        @Override
        protected Boolean doInBackground(Void... params)
        {
            try
            {
                Facebook facebook = FacebookProvider.getFacebook();
                Bundle bparams = new Bundle();
                bparams.putString("fields", "name");
                String response = facebook.request("me", bparams);
                JSONObject jsonObject = new JSONObject(response);

                name = jsonObject.getString("name");
                return true;
            } catch (Exception ex)
            {
                GuiUtils.error(TAG,
                        R.string.errorCouldNotRetrieveFacebookScreenName,
                        ex,
                        activity);
            }
            return false;
        }

        @Override
        protected void onSuccessPostExecute() {
            loggedInAsText.setText(CommonUtils.getStringResource(
                            R.string.share_facebook_logged_in_as,
                            name));
        }
    }

    private class PostPhotoTask extends
            AsyncTaskEx<Void, Void, Boolean>
    {
        Photo photo;

        PostPhotoTask(Photo photo)
        {
            this.photo = photo;
        }

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            sendButton.setEnabled(false);
            loadingControl.startLoading();
        }

        @Override
        protected Boolean doInBackground(Void... params)
        {
            return sharePhoto(photo);
        }

        @Override
        protected void onPostExecute(Boolean result)
        {
            super.onPostExecute(result);
            loadingControl.stopLoading();
            if (result.booleanValue())
            {
                GuiUtils.info(
                        R.string.share_facebook_success_message);
            }
            Dialog dialog = FacebookFragment.this.getDialog();
            if (dialog != null && dialog.isShowing())
            {
                FacebookFragment.this.dismissAllowingStateLoss();
            }
        }
    }

    boolean sharePhoto(Photo photo)
    {
        try
        {
            sharePhoto(messageEt.getText().toString(), photo, thumbSize,
                    true);
            return true;
        } catch (Exception ex)
        {
            GuiUtils.error(TAG, R.string.errorCouldNotSendFacebookPhoto,
                    ex,
                    getActivity());
        }
        return false;
    }
    /**
     * @param message the sharing message
     * @param photo the photo to share
     * @param thumbSize the thumb image size
     * @throws FileNotFoundException
     * @throws MalformedURLException
     * @throws IOException
     */
    public static void sharePhoto(
            String message,
            Photo photo,
            ReturnSizes thumbSize) throws FileNotFoundException,
            MalformedURLException, IOException
    {
        sharePhoto(message, photo, thumbSize, true);
    }

    /**
     * @param message the sharing message
     * @param photo the photo to share
     * @param thumbSize the thumb image size
     * @param appendToken whether to append share token to the photo url
     * @throws FileNotFoundException
     * @throws MalformedURLException
     * @throws IOException
     */
    public static void sharePhoto(
            String message,
            Photo photo,
            ReturnSizes thumbSize,
            boolean appendToken) throws FileNotFoundException,
            MalformedURLException, IOException
    {
        Facebook facebook = FacebookProvider.getFacebook();
        Bundle bparams = new Bundle();
        bparams.putString(
                "message",
                message);
        bparams.putString(
                "name",
                photo.getTitle());
        bparams.putString(
                "caption",
                photo.getTitle());
        bparams.putString("description", CommonUtils
                .getStringResource(R.string.share_facebook_default_description));
        bparams.putString("picture", photo.getUrl(thumbSize.toString()));
        bparams.putString("link", PhotoUtils.getShareUrl(photo, appendToken));
        TrackerUtils.trackSocial("facebook", "feed",
                message + " | " + photo.getUrl(Photo.URL));
        facebook.request("feed", bparams, "POST");
    }

    public static interface FacebookLoadingControlAccessor
    {
        LoadingControl getFacebookLoadingControl();
    }
}
