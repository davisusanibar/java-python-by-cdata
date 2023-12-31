import jpype
import pyarrow as pa
import pyarrow.dataset as ds
import sys
from pyarrow.cffi import ffi

def getRecordBatchReader(py_stream_ptr):
    generator = getIterableRecordBatchReader(py_stream_ptr)
    schema = next(generator)
    return pa.RecordBatchReader.from_batches(schema, generator)

def getIterableRecordBatchReader(py_stream_ptr):
    with pa.RecordBatchReader._import_from_c(py_stream_ptr) as reader: #Import Schema from a C ArrowSchema struct, given its pointer.
        yield reader.schema
        yield from reader

jvmargs=["-Darrow.memory.debug.allocator=true"]
jpype.startJVM(classpath=[
    "./target/java-python-by-cdata-1.0-SNAPSHOT-jar-with-dependencies.jar"])
java_reader_api = jpype.JClass('org.example.cdata.ShareArrowReaderAPI')
java_c_package = jpype.JPackage("org").apache.arrow.c
py_stream = ffi.new("struct ArrowArrayStream*")
py_stream_ptr = int(ffi.cast("uintptr_t", py_stream))
java_wrapped_stream = java_c_package.ArrowArrayStream.wrap(py_stream_ptr)
java_c_package.Data.exportArrayStream(
    java_reader_api.getAllocatorForJavaConsumers(),
    java_reader_api.getArrowReaderForJavaConsumers(int(sys.argv[1]), # batchSize = int(sys.argv[1])
                                                   eval(sys.argv[2])), # reuseVSR = eval(sys.argv[2]
    java_wrapped_stream)

with getRecordBatchReader(py_stream_ptr) as streamsReaderForJava:
    ds.write_dataset(streamsReaderForJava,
                     './jdbc/parquet',
                     format="parquet")

java_reader_api.closeAllocatorForJavaConsumers();

