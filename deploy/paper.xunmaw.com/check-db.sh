#!/bin/bash
mysql -S /tmp/mysql.sock -uroot -p123QWER. ai_sc -e "SHOW TABLES LIKE 'uc_membership_feature_quota';"
mysql -S /tmp/mysql.sock -uroot -p123QWER. ai_sc -e "SHOW TABLES LIKE 'paper_session';"
mysql -S /tmp/mysql.sock -uroot -p123QWER. -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ai_sc';"
