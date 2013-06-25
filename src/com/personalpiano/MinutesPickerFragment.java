package com.personalpiano;

import com.example.personalpiano.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.widget.NumberPicker;

public class MinutesPickerFragment extends DialogFragment {
	public interface MinutesPickerListener{
		public void onSelect(int minute);
		public void onCancel(DialogFragment dialog);
	}
	private MinutesPickerListener mListener;
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final NumberPicker np = new NumberPicker(getActivity());
		np.setMaxValue(60);
		np.setMinValue(5);
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.pick_minutes)
		.setView(np);
		
		builder.setPositiveButton(R.string.set, new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mListener.onSelect(np.getValue());
			}
		})
		.setNegativeButton(R.string.cancel, new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mListener.onCancel(MinutesPickerFragment.this);
			}
		});
		
		return builder.create();
	}
	
	@Override
	public void onAttach(Activity activity) {
		// TODO Auto-generated method stub
		super.onAttach(activity);
		mListener = (MinutesPickerListener) activity;
	}

}
