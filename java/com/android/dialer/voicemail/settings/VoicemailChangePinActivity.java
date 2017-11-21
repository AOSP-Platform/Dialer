/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.dialer.voicemail.settings;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.PinChanger;
import com.android.voicemail.PinChanger.ChangePinResult;
import com.android.voicemail.PinChanger.PinSpecification;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailComponent;
import java.lang.ref.WeakReference;

/**
 * Dialog to change the voicemail PIN. The TUI (Telephony User Interface) PIN is used when accessing
 * traditional voicemail through phone call. The intent to launch this activity must contain {@link
 * VoicemailClient#PARAM_PHONE_ACCOUNT_HANDLE}
 */
@TargetApi(VERSION_CODES.O)
public class VoicemailChangePinActivity extends Activity
    implements OnClickListener, OnEditorActionListener, TextWatcher {

  private static final String TAG = "VmChangePinActivity";
  public static final String ACTION_CHANGE_PIN = "com.android.dialer.action.CHANGE_PIN";

  private static final int MESSAGE_HANDLE_RESULT = 1;

  private PhoneAccountHandle mPhoneAccountHandle;
  private PinChanger mPinChanger;

  private static class ChangePinParams {
    PinChanger pinChanger;
    PhoneAccountHandle phoneAccountHandle;
    String oldPin;
    String newPin;
  }

  private DialerExecutor<ChangePinParams> mChangePinExecutor;

  private int mPinMinLength;
  private int mPinMaxLength;

  private State mUiState = State.Initial;
  private String mOldPin;
  private String mFirstPin;

  private ProgressDialog mProgressDialog;

  private TextView mHeaderText;
  private TextView mHintText;
  private TextView mErrorText;
  private EditText mPinEntry;
  private Button mCancelButton;
  private Button mNextButton;

  private Handler mHandler = new ChangePinHandler(new WeakReference<>(this));

  private enum State {
    /**
     * Empty state to handle initial state transition. Will immediately switch into {@link
     * #VerifyOldPin} if a default PIN has been set by the OMTP client, or {@link #EnterOldPin} if
     * not.
     */
    Initial,
    /**
     * Prompt the user to enter old PIN. The PIN will be verified with the server before proceeding
     * to {@link #EnterNewPin}.
     */
    EnterOldPin {
      @Override
      public void onEnter(VoicemailChangePinActivity activity) {
        activity.setHeader(R.string.change_pin_enter_old_pin_header);
        activity.mHintText.setText(R.string.change_pin_enter_old_pin_hint);
        activity.mNextButton.setText(R.string.change_pin_continue_label);
        activity.mErrorText.setText(null);
      }

      @Override
      public void onInputChanged(VoicemailChangePinActivity activity) {
        activity.setNextEnabled(activity.getCurrentPasswordInput().length() > 0);
      }

      @Override
      public void handleNext(VoicemailChangePinActivity activity) {
        activity.mOldPin = activity.getCurrentPasswordInput();
        activity.verifyOldPin();
      }

      @Override
      public void handleResult(VoicemailChangePinActivity activity, @ChangePinResult int result) {
        if (result == PinChanger.CHANGE_PIN_SUCCESS) {
          activity.updateState(State.EnterNewPin);
        } else {
          CharSequence message = activity.getChangePinResultMessage(result);
          activity.showError(message);
          activity.mPinEntry.setText("");
        }
      }
    },
    /**
     * The default old PIN is found. Show a blank screen while verifying with the server to make
     * sure the PIN is still valid. If the PIN is still valid, proceed to {@link #EnterNewPin}. If
     * not, the user probably changed the PIN through other means, proceed to {@link #EnterOldPin}.
     * If any other issue caused the verifying to fail, show an error and exit.
     */
    VerifyOldPin {
      @Override
      public void onEnter(VoicemailChangePinActivity activity) {
        activity.findViewById(android.R.id.content).setVisibility(View.INVISIBLE);
        activity.verifyOldPin();
      }

      @Override
      public void handleResult(
          final VoicemailChangePinActivity activity, @ChangePinResult int result) {
        if (result == PinChanger.CHANGE_PIN_SUCCESS) {
          activity.updateState(State.EnterNewPin);
        } else if (result == PinChanger.CHANGE_PIN_SYSTEM_ERROR) {
          activity
              .getWindow()
              .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
          activity.showError(
              activity.getString(R.string.change_pin_system_error),
              new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                  activity.finish();
                }
              });
        } else {
          LogUtil.e(TAG, "invalid default old PIN: " + activity.getChangePinResultMessage(result));
          // If the default old PIN is rejected by the server, the PIN is probably changed
          // through other means, or the generated pin is invalid
          // Wipe the default old PIN so the old PIN input box will be shown to the user
          // on the next time.
          activity.mPinChanger.setScrambledPin(null);
          activity.updateState(State.EnterOldPin);
        }
      }

      @Override
      public void onLeave(VoicemailChangePinActivity activity) {
        activity.findViewById(android.R.id.content).setVisibility(View.VISIBLE);
      }
    },
    /**
     * Let the user enter the new PIN and validate the format. Only length is enforced, PIN strength
     * check relies on the server. After a valid PIN is entered, proceed to {@link #ConfirmNewPin}
     */
    EnterNewPin {
      @Override
      public void onEnter(VoicemailChangePinActivity activity) {
        activity.mHeaderText.setText(R.string.change_pin_enter_new_pin_header);
        activity.mNextButton.setText(R.string.change_pin_continue_label);
        activity.mHintText.setText(
            activity.getString(
                R.string.change_pin_enter_new_pin_hint,
                activity.mPinMinLength,
                activity.mPinMaxLength));
      }

      @Override
      public void onInputChanged(VoicemailChangePinActivity activity) {
        String password = activity.getCurrentPasswordInput();
        if (password.length() == 0) {
          activity.setNextEnabled(false);
          return;
        }
        CharSequence error = activity.validatePassword(password);
        if (error != null) {
          activity.mErrorText.setText(error);
          activity.setNextEnabled(false);
        } else {
          activity.mErrorText.setText(null);
          activity.setNextEnabled(true);
        }
      }

      @Override
      public void handleNext(VoicemailChangePinActivity activity) {
        CharSequence errorMsg;
        errorMsg = activity.validatePassword(activity.getCurrentPasswordInput());
        if (errorMsg != null) {
          activity.showError(errorMsg);
          return;
        }
        activity.mFirstPin = activity.getCurrentPasswordInput();
        activity.updateState(State.ConfirmNewPin);
      }
    },
    /**
     * Let the user type in the same PIN again to avoid typos. If the PIN matches then perform a PIN
     * change to the server. Finish the activity if succeeded. Return to {@link #EnterOldPin} if the
     * old PIN is rejected, {@link #EnterNewPin} for other failure.
     */
    ConfirmNewPin {
      @Override
      public void onEnter(VoicemailChangePinActivity activity) {
        activity.mHeaderText.setText(R.string.change_pin_confirm_pin_header);
        activity.mHintText.setText(null);
        activity.mNextButton.setText(R.string.change_pin_ok_label);
      }

      @Override
      public void onInputChanged(VoicemailChangePinActivity activity) {
        if (activity.getCurrentPasswordInput().length() == 0) {
          activity.setNextEnabled(false);
          return;
        }
        if (activity.getCurrentPasswordInput().equals(activity.mFirstPin)) {
          activity.setNextEnabled(true);
          activity.mErrorText.setText(null);
        } else {
          activity.setNextEnabled(false);
          activity.mErrorText.setText(R.string.change_pin_confirm_pins_dont_match);
        }
      }

      @Override
      public void handleResult(VoicemailChangePinActivity activity, @ChangePinResult int result) {
        if (result == PinChanger.CHANGE_PIN_SUCCESS) {
          // If the PIN change succeeded we no longer know what the old (current) PIN is.
          // Wipe the default old PIN so the old PIN input box will be shown to the user
          // on the next time.
          activity.mPinChanger.setScrambledPin(null);

          activity.finish();
          Logger.get(activity).logImpression(DialerImpression.Type.VVM_CHANGE_PIN_COMPLETED);
          Toast.makeText(
                  activity, activity.getString(R.string.change_pin_succeeded), Toast.LENGTH_SHORT)
              .show();
        } else {
          CharSequence message = activity.getChangePinResultMessage(result);
          LogUtil.i(TAG, "Change PIN failed: " + message);
          activity.showError(message);
          if (result == PinChanger.CHANGE_PIN_MISMATCH) {
            // Somehow the PIN has changed, prompt to enter the old PIN again.
            activity.updateState(State.EnterOldPin);
          } else {
            // The new PIN failed to fulfil other restrictions imposed by the server.
            activity.updateState(State.EnterNewPin);
          }
        }
      }

      @Override
      public void handleNext(VoicemailChangePinActivity activity) {
        activity.processPinChange(activity.mOldPin, activity.mFirstPin);
      }
    };

    /** The activity has switched from another state to this one. */
    public void onEnter(VoicemailChangePinActivity activity) {
      // Do nothing
    }

    /**
     * The user has typed something into the PIN input field. Also called after {@link
     * #onEnter(VoicemailChangePinActivity)}
     */
    public void onInputChanged(VoicemailChangePinActivity activity) {
      // Do nothing
    }

    /** The asynchronous call to change the PIN on the server has returned. */
    public void handleResult(VoicemailChangePinActivity activity, @ChangePinResult int result) {
      // Do nothing
    }

    /** The user has pressed the "next" button. */
    public void handleNext(VoicemailChangePinActivity activity) {
      // Do nothing
    }

    /** The activity has switched from this state to another one. */
    public void onLeave(VoicemailChangePinActivity activity) {
      // Do nothing
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mPhoneAccountHandle =
        getIntent().getParcelableExtra(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE);
    mPinChanger =
        VoicemailComponent.get(this)
            .getVoicemailClient()
            .createPinChanger(getApplicationContext(), mPhoneAccountHandle);
    setContentView(R.layout.voicemail_change_pin);
    setTitle(R.string.change_pin_title);

    readPinLength();

    View view = findViewById(android.R.id.content);

    mCancelButton = (Button) view.findViewById(R.id.cancel_button);
    mCancelButton.setOnClickListener(this);
    mNextButton = (Button) view.findViewById(R.id.next_button);
    mNextButton.setOnClickListener(this);

    mPinEntry = (EditText) view.findViewById(R.id.pin_entry);
    mPinEntry.setOnEditorActionListener(this);
    mPinEntry.addTextChangedListener(this);
    if (mPinMaxLength != 0) {
      mPinEntry.setFilters(new InputFilter[] {new LengthFilter(mPinMaxLength)});
    }

    mHeaderText = (TextView) view.findViewById(R.id.headerText);
    mHintText = (TextView) view.findViewById(R.id.hintText);
    mErrorText = (TextView) view.findViewById(R.id.errorText);

    mChangePinExecutor =
        DialerExecutorComponent.get(this)
            .dialerExecutorFactory()
            .createUiTaskBuilder(getFragmentManager(), "changePin", new ChangePinWorker())
            .onSuccess(this::sendResult)
            .onFailure((tr) -> sendResult(PinChanger.CHANGE_PIN_SYSTEM_ERROR))
            .build();

    if (isPinScrambled(this, mPhoneAccountHandle)) {
      mOldPin = mPinChanger.getScrambledPin();
      updateState(State.VerifyOldPin);
    } else {
      updateState(State.EnterOldPin);
    }
  }

  /** Extracts the pin length requirement sent by the server with a STATUS SMS. */
  private void readPinLength() {
    PinSpecification pinSpecification = mPinChanger.getPinSpecification();
    mPinMinLength = pinSpecification.minLength;
    mPinMaxLength = pinSpecification.maxLength;
  }

  @Override
  public void onResume() {
    super.onResume();
    updateState(mUiState);
  }

  public void handleNext() {
    if (mPinEntry.length() == 0) {
      return;
    }
    mUiState.handleNext(this);
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.next_button) {
      handleNext();
    } else if (v.getId() == R.id.cancel_button) {
      finish();
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
    if (!mNextButton.isEnabled()) {
      return true;
    }
    // Check if this was the result of hitting the enter or "done" key
    if (actionId == EditorInfo.IME_NULL
        || actionId == EditorInfo.IME_ACTION_DONE
        || actionId == EditorInfo.IME_ACTION_NEXT) {
      handleNext();
      return true;
    }
    return false;
  }

  @Override
  public void afterTextChanged(Editable s) {
    mUiState.onInputChanged(this);
  }

  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    // Do nothing
  }

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
    // Do nothing
  }

  /**
   * After replacing the default PIN with a random PIN, call this to store the random PIN. The
   * stored PIN will be automatically entered when the user attempts to change the PIN.
   */
  public static boolean isPinScrambled(Context context, PhoneAccountHandle phoneAccountHandle) {
    return VoicemailComponent.get(context)
            .getVoicemailClient()
            .createPinChanger(context, phoneAccountHandle)
            .getScrambledPin()
        != null;
  }

  private String getCurrentPasswordInput() {
    return mPinEntry.getText().toString();
  }

  private void updateState(State state) {
    State previousState = mUiState;
    mUiState = state;
    if (previousState != state) {
      previousState.onLeave(this);
      mPinEntry.setText("");
      mUiState.onEnter(this);
    }
    mUiState.onInputChanged(this);
  }

  /**
   * Validates PIN and returns a message to display if PIN fails test.
   *
   * @param password the raw password the user typed in
   * @return error message to show to user or null if password is OK
   */
  private CharSequence validatePassword(String password) {
    if (mPinMinLength == 0 && mPinMaxLength == 0) {
      // Invalid length requirement is sent by the server, just accept anything and let the
      // server decide.
      return null;
    }

    if (password.length() < mPinMinLength) {
      return getString(R.string.vm_change_pin_error_too_short);
    }
    return null;
  }

  private void setHeader(int text) {
    mHeaderText.setText(text);
    mPinEntry.setContentDescription(mHeaderText.getText());
  }

  /**
   * Get the corresponding message for the {@link ChangePinResult}.<code>result</code> must not
   * {@link PinChanger#CHANGE_PIN_SUCCESS}
   */
  private CharSequence getChangePinResultMessage(@ChangePinResult int result) {
    switch (result) {
      case PinChanger.CHANGE_PIN_TOO_SHORT:
        return getString(R.string.vm_change_pin_error_too_short);
      case PinChanger.CHANGE_PIN_TOO_LONG:
        return getString(R.string.vm_change_pin_error_too_long);
      case PinChanger.CHANGE_PIN_TOO_WEAK:
        return getString(R.string.vm_change_pin_error_too_weak);
      case PinChanger.CHANGE_PIN_INVALID_CHARACTER:
        return getString(R.string.vm_change_pin_error_invalid);
      case PinChanger.CHANGE_PIN_MISMATCH:
        return getString(R.string.vm_change_pin_error_mismatch);
      case PinChanger.CHANGE_PIN_SYSTEM_ERROR:
        return getString(R.string.vm_change_pin_error_system_error);
      default:
        LogUtil.e(TAG, "Unexpected ChangePinResult " + result);
        return null;
    }
  }

  private void verifyOldPin() {
    processPinChange(mOldPin, mOldPin);
  }

  private void setNextEnabled(boolean enabled) {
    mNextButton.setEnabled(enabled);
  }

  private void showError(CharSequence message) {
    showError(message, null);
  }

  private void showError(CharSequence message, @Nullable OnDismissListener callback) {
    new AlertDialog.Builder(this)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .setOnDismissListener(callback)
        .show();
  }

  /** Asynchronous call to change the PIN on the server. */
  private void processPinChange(String oldPin, String newPin) {
    mProgressDialog = new ProgressDialog(this);
    mProgressDialog.setCancelable(false);
    mProgressDialog.setMessage(getString(R.string.vm_change_pin_progress_message));
    mProgressDialog.show();

    ChangePinParams params = new ChangePinParams();
    params.pinChanger = mPinChanger;
    params.phoneAccountHandle = mPhoneAccountHandle;
    params.oldPin = oldPin;
    params.newPin = newPin;

    mChangePinExecutor.executeSerial(params);
  }

  private void sendResult(@ChangePinResult int result) {
    LogUtil.i(TAG, "Change PIN result: " + result);
    if (mProgressDialog.isShowing()
        && !VoicemailChangePinActivity.this.isDestroyed()
        && !VoicemailChangePinActivity.this.isFinishing()) {
      mProgressDialog.dismiss();
    } else {
      LogUtil.i(TAG, "Dialog not visible, not dismissing");
    }
    mHandler.obtainMessage(MESSAGE_HANDLE_RESULT, result, 0).sendToTarget();
  }

  private static class ChangePinHandler extends Handler {

    private final WeakReference<VoicemailChangePinActivity> activityWeakReference;

    private ChangePinHandler(WeakReference<VoicemailChangePinActivity> activityWeakReference) {
      this.activityWeakReference = activityWeakReference;
    }

    @Override
    public void handleMessage(Message message) {
      VoicemailChangePinActivity activity = activityWeakReference.get();
      if (activity == null) {
        return;
      }
      if (message.what == MESSAGE_HANDLE_RESULT) {
        activity.mUiState.handleResult(activity, message.arg1);
      }
    }
  }

  private static class ChangePinWorker implements Worker<ChangePinParams, Integer> {

    @Nullable
    @Override
    public Integer doInBackground(@Nullable ChangePinParams input) throws Throwable {
      return input.pinChanger.changePin(input.oldPin, input.newPin);
    }
  }
}
