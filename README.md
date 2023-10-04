# Apache Arrow JDBC Adapter + C Data Interface + PyArrow + CFFI

## Purpose
Demo project to share references between Java and Python using Apache Arrow.

## Testing:
1. Create jar with dependencies: `mvn clean package`
2. Create conda environment: `source src/main/resources/env/local.sh create_conda_env_for_cdata_java_python`
3. Activate conda environment: `conda activate cdata_java_pythone`
4. Print log for data read: `python src/main/java/org/example/consumer/consumerArrowReaderAPI.py 2 True log`
5. Create parquet file: `python src/main/java/org/example/consumer/consumerArrowReaderAPI.py 2 True parquet`
6. Validate parquet files: `parquet-tools cat jdbc/parquet/part-0.parquet`