package xyz.zedler.patrick.tack.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import xyz.zedler.patrick.tack.Constants;
import xyz.zedler.patrick.tack.R;
import xyz.zedler.patrick.tack.behavior.ScrollBehavior;
import xyz.zedler.patrick.tack.databinding.ActivityAboutBinding;
import xyz.zedler.patrick.tack.fragment.ChangelogBottomSheetDialogFragment;
import xyz.zedler.patrick.tack.fragment.TextBottomSheetDialogFragment;
import xyz.zedler.patrick.tack.util.ClickUtil;
import xyz.zedler.patrick.tack.util.ViewUtil;

public class AboutActivity extends AppCompatActivity implements View.OnClickListener {

	private final static String TAG = AboutActivity.class.getSimpleName();

	private ActivityAboutBinding binding;
	private final ClickUtil clickUtil = new ClickUtil();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		binding = ActivityAboutBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());

		binding.frameAboutClose.setOnClickListener(v -> {
			if (clickUtil.isDisabled()) return;
			finish();
		});

		new ScrollBehavior().setUpScroll(
				this,
				binding.appBarAbout,
				binding.linearAboutAppBar,
				binding.scrollAbout,
				true
		);

		ViewUtil.setOnClickListeners(
				this,
				binding.linearChangelog,
				binding.linearDeveloper,
				binding.linearLicenseMetronome,
				binding.linearLicenseMaterialComponents,
				binding.linearLicenseMaterialIcons,
				binding.linearLicenseEdwin
		);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		binding = null;
	}

	@Override
	public void onClick(View v) {
		if (clickUtil.isDisabled()) return;

		int id = v.getId();
		if (id == R.id.linear_changelog) {
			ViewUtil.startAnimatedIcon(binding.imageChangelog);
			BottomSheetDialogFragment fragment = new ChangelogBottomSheetDialogFragment();
			fragment.show(getSupportFragmentManager(), fragment.toString());
		} else if (id == R.id.linear_developer) {
			ViewUtil.startAnimatedIcon(binding.imageDeveloper);
			new Handler(Looper.getMainLooper()).postDelayed(() -> startActivity(
					new Intent(
							Intent.ACTION_VIEW,
							Uri.parse("http://play.google.com/store/apps/dev?id=" +
											"8122479227040208191"
							)
					)), 300
			);
		} else if (id == R.id.linear_license_metronome) {
			ViewUtil.startAnimatedIcon(binding.imageLicenseMetronome);
			showTextBottomSheet(
					"apache",
					R.string.license_metronome,
					R.string.license_metronome_link
			);
		} else if (id == R.id.linear_license_material_components) {
			ViewUtil.startAnimatedIcon(binding.imageLicenseMaterialComponents);
			showTextBottomSheet(
					"apache",
					R.string.license_material_components,
					R.string.license_material_components_link
			);
		} else if (id == R.id.linear_license_material_icons) {
			ViewUtil.startAnimatedIcon(binding.imageLicenseMaterialIcons);
			showTextBottomSheet(
					"apache",
					R.string.license_material_icons,
					R.string.license_material_icons_link
			);
		} else if (id == R.id.linear_license_edwin) {
			ViewUtil.startAnimatedIcon(binding.imageLicenseEdwin);
			showTextBottomSheet(
					"open_font",
					R.string.license_edwin,
					R.string.license_edwin_link
			);
		}
	}

	private void showTextBottomSheet(String file, @StringRes int title, @StringRes int link) {
		DialogFragment fragment = new TextBottomSheetDialogFragment();
		Bundle bundle = new Bundle();
		bundle.putString(Constants.EXTRA.TITLE, getString(title));
		bundle.putString(Constants.EXTRA.FILE, file);
		bundle.putString(Constants.EXTRA.LINK, getString(link));
		fragment.setArguments(bundle);
		fragment.show(getSupportFragmentManager(), fragment.toString());
	}
}
