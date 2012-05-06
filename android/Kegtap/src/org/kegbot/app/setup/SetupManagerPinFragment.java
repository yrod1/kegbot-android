package org.kegbot.app.setup;

import org.kegbot.app.util.PreferenceHelper;
import org.kegbot.app.R;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

public class SetupManagerPinFragment extends SetupFragment {

  private View mView;
  private EditText mPinText;
  private EditText mPinConfirmText;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    mView = inflater.inflate(R.layout.setup_manager_pin_fragment, null);
    mView.setBackgroundDrawable(getResources().getDrawable(R.drawable.shape_rounded_rect));

    PreferenceHelper prefs = new PreferenceHelper(getActivity());

    mPinText = (EditText) mView.findViewById(R.id.managerPin);
    mPinText.setText(prefs.getPin());
    mPinText.addTextChangedListener(new TextWatcher() {
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        mPinConfirmText.setText("");
      }

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });
    mPinConfirmText = (EditText) mView.findViewById(R.id.managerPinConfirm);
    mPinConfirmText.setText(prefs.getPin());

    return mView;
  }

  public String getPin() {
    return mPinText.getText().toString();
  }

  public String getConfirmedPin() {
    return mPinConfirmText.getText().toString();
  }

  @Override
  public String validate() {
    if (!getPin().equals(getConfirmedPin())) {
      return "Pins do not match.";
    }
    PreferenceHelper prefs = new PreferenceHelper(getActivity());
    prefs.setPin(getPin());
    return "";
  }

  @Override
  protected void onValidationFailed() {
    clearPins();
  }

  private void clearPins() {
    mPinText.setText("");
    mPinConfirmText.setText("");
    mPinText.requestFocus();
  }

}