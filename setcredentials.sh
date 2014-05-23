#!/bin/sh

echo -n "Please enter username: "
read var_username
echo -n "Please enter password: "
stty_orig=`stty -g`
stty -echo
read var_password
stty $stty_orig

echo "\n"
java -jar SetCredentials-0.4.5.jar $var_username $var_password
