-- database: presto; groups: mysql_connector; queryType: SELECT; tables: mysql.workers_jdbc
--!
describe mysql.test.workers_jdbc
--!
-- delimiter: |; trimValues: true; ignoreOrder: true;
id_employee        | bigint  | |
first_name         | varchar | |
last_name          | varchar | |
date_of_employment | date    | |
department         | bigint  | |
id_department      | bigint  | |
name               | varchar | |
salary             | bigint  | |
