# Apache Arrow JDBC Adapter + C Data Interface + PyArrow + CFFI

## Purpose
Demo project to share references between Java and Python using Apache Arrow.

## Testing:
1. Create jar with dependencies: `mvn clean package`
2. Print log for data read: `python src/main/java/org/example/consumer/consumerReaderAPI.py 2 True log`
3. Create parquet file: `python src/main/java/org/example/consumer/consumerReaderAPI.py 2 True parquet`
4. Validate parquet files: `parquet-tools cat jdbc/parquet/part-0.parquet`