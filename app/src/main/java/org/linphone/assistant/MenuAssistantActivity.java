package org.linphone.assistant;

/*
MenuAssistantActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.R;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public class MenuAssistantActivity extends AssistantActivity {
    private TextView mAccountCreation, mAccountConnection, mGenericConnection, mRemoteConfiguration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_menu);

        Log.e("###");

        mAccountCreation = findViewById(R.id.account_creation);
        mAccountCreation.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent;
                        if (getResources().getBoolean(R.bool.isTablet)
                                || !getResources().getBoolean(R.bool.use_phone_number_validation)) {
                            intent =
                                    new Intent(
                                            MenuAssistantActivity.this,
                                            EmailAccountCreationAssistantActivity.class);
                        } else {
                            intent =
                                    new Intent(
                                            MenuAssistantActivity.this,
                                            PhoneAccountCreationAssistantActivity.class);
                        }
                        startActivity(intent);
                    }
                });

        mAccountConnection = findViewById(R.id.account_connection);
        mAccountConnection.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(
                                new Intent(
                                        MenuAssistantActivity.this,
                                        AccountConnectionAssistantActivity.class));
                    }
                });
        if (getResources().getBoolean(R.bool.hide_linphone_accounts_in_assistant)) {
            mAccountConnection.setVisibility(View.GONE);
        }

        mGenericConnection = findViewById(R.id.generic_connection);
        mGenericConnection.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(
                                new Intent(
                                        MenuAssistantActivity.this,
                                        GenericConnectionAssistantActivity.class));
                    }
                });
        if (getResources().getBoolean(R.bool.hide_generic_accounts_in_assistant)) {
            mGenericConnection.setVisibility(View.GONE);
        }

        mRemoteConfiguration = findViewById(R.id.remote_configuration);
        mRemoteConfiguration.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(
                                new Intent(
                                        MenuAssistantActivity.this,
                                        RemoteConfigurationAssistantActivity.class));
                    }
                });
        if (getResources().getBoolean(R.bool.hide_remote_provisioning_in_assistant)) {
            mRemoteConfiguration.setVisibility(View.GONE);
        }

        if (getResources().getBoolean(R.bool.assistant_use_linphone_login_as_first_fragment)) {
            startActivity(
                    new Intent(
                            MenuAssistantActivity.this, AccountConnectionAssistantActivity.class));
            finish();
        } else if (getResources()
                .getBoolean(R.bool.assistant_use_generic_login_as_first_fragment)) {
            startActivity(
                    new Intent(
                            MenuAssistantActivity.this, GenericConnectionAssistantActivity.class));
            finish();
        } else if (getResources()
                .getBoolean(R.bool.assistant_use_create_linphone_account_as_first_fragment)) {
            startActivity(
                    new Intent(
                            MenuAssistantActivity.this,
                            PhoneAccountCreationAssistantActivity.class));
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!getResources()
                .getBoolean(R.bool.forbid_to_leave_assistant_before_account_configuration)) {
            mBack.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            LinphonePreferences.instance().firstLaunchSuccessful();
                            goToLinphoneActivity();
                        }
                    });
        } else {
            mBack.setEnabled(false);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (getResources()
                    .getBoolean(R.bool.forbid_to_leave_assistant_before_account_configuration)) {
                // Do nothing
                return true;
            } else {
                LinphonePreferences.instance().firstLaunchSuccessful();
                goToLinphoneActivity();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
