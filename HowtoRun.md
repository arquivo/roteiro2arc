# How to Run #
Roteiro2Arc conversion is a two step process:
  1. Search the files for an the embedded original URL and write to a database the information that relates the local path of the file to the found URL.
  1. Reads the files and export them ARC files. This second steps is further divided in sub-steps:
    1. Read the files that we know the original URL(it is in the DB), convert the local paths in the file to URLs and write it to an ARC file. During this step, every file that had no known original URL receives a generated URL (we use the URL of the file that called it and append the local path of the unknown file) and a reference to this	file and its generated URL is stored in a special recovery DB.
    1. The recovery DB is read and every file referenced is exported to ARC files using the generated URL. This way, it is possible that webpages can maintain the reference to images, stylesheets and other 	resources.

The easiest way to run Roteiro2Arc is to use the "run" Ant task.
This task handles both steps of running the conversion process:

```
> ant run -Dsource=<source_dir> -Ddestination=<destination_dir> \
-Ddb=<db_dir> -Drecovery-db=<recovery-db_dir>```

The other way is to run each step separately:

1. URL extraction
> ```
> ant run-url-extraction -Dsource=<source_dir> -Ddb=<db_dir>``` (with Ant)
> ```
> java ExtractURL <source_dir> <db_dir>``` (calling the class)

2. Writing the ARC files
> ```
> ant run-arc-conversion -Dsource=<source_dir> -Ddestination=<destination_dir> \
-Ddb=<db_dir> -Drecovery-db=<recovery-db_dir>``` (with Ant)

> ```
> java ArcConverter <source_dir> <destination_dir> <db_dir> \ <recovery-db_dir>```(calling the class)

  * _source\_dir_: the dir with the local files.
  * _destination\_dir_: the destination for the converted ARC files.
  * _db\_dir_: the path where the Berkeley DB with the detected paths/URLs will be stored.
  * _recovery-db\_dir_: the path where the Berkeley DB with the recovery 	information of the files without the original URL. This	DB will hold path/generated-url informations.