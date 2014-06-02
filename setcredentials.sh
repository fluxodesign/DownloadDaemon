#!/bin/sh

echo -n "Please enter username: "
read var_username
echo -n "Please enter password: "
stty_orig=`stty -g`
stty -echo
read var_password
echo -n "Retype new password: "
read var_retyped
stty $stty_orig

echo "\n"
if [ "$var_password" = "$var_retyped" ]; then
	java -jar SetCredentials-0.4.5.jar $var_username $var_password
else
	echo "Passwords do not match. Please rerun the script."
fi
