import jpype
import pyarrow as pa
import pyarrow.csv as csv
import pyarrow.dataset as ds
import sys
from pyarrow.cffi import ffi


def getRecordBatchReader(py_stream_ptr):
    generator = getIterableRecordBatchReader(py_stream_ptr)
    schema = next(generator)
    return pa.RecordBatchReader.from_batches(schema, generator)


def getIterableRecordBatchReader(py_stream_ptr):
    with pa.RecordBatchReader._import_from_c(py_stream_ptr) as reader:
        yield reader.schema
        yield from reader


# batchSize = int(sys.argv[1]), reuseVSR = eval(sys.argv[2], log|parquet = str(sys.argv[3])
jpype.startJVM(classpath=[
    "./target/java-python-by-cdata-1.0-SNAPSHOT-jar-with-dependencies.jar"])
java_reader_api = jpype.JClass('org.example.cdata.JavaReaderApi')
java_c_package = jpype.JPackage("org").apache.arrow.c
py_stream = ffi.new("struct ArrowArrayStream*")
py_stream_ptr = int(ffi.cast("uintptr_t", py_stream))
java_wrapped_stream = java_c_package.ArrowArrayStream.wrap(py_stream_ptr)
# get reader data exported into memoryAddress
print('Python Parameters: BatchSize = ' + sys.argv[1] + ', reuseVSR = ' +
      sys.argv[2])
java_c_package.Data.exportArrayStream(
    java_reader_api.getAllocatorForJavaConsumers(),
    java_reader_api.getArrowReaderForJavaConsumers(int(sys.argv[1]),
                                                   eval(sys.argv[2])),
    java_wrapped_stream)
with getRecordBatchReader(py_stream_ptr) as streamsReaderForJava:
    # print logs
    if str(sys.argv[3]) == 'log':
        for batch in streamsReaderForJava:
            print(batch.num_rows)
            print(batch.num_columns)
            print(batch.to_pylist())
    # create parquet file
    elif str(sys.argv[3]) == 'parquet':
        ds.write_dataset(streamsReaderForJava,
                         './jdbc/parquet',
                         format="parquet")
    # create csv file
    elif str(sys.argv[3]) == 'csv':
        with csv.CSVWriter('./jdbc/csv',
                           streamsReaderForJava.schema) as writer:
            for record_batch in streamsReaderForJava:
                writer.write_batch(record_batch)
    else:
        print('Invalid parameter. Values supported are: {log, parquet, csv}')
