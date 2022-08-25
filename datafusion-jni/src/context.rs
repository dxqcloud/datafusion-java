use std::io::BufReader;
use std::sync::Arc;
use arrow::ipc::reader::FileReader;
use datafusion::datasource::MemTable;
use datafusion::execution::context::SessionContext;
use datafusion::execution::context;
use datafusion::execution::context::TaskProperties::SessionConfig;
use datafusion::prelude::{CsvReadOptions, ParquetReadOptions};
use jni::objects::{JClass, JObject, JString, JValue};
use jni::sys::jbyteArray;
use jni::sys::jlong;
use jni::JNIEnv;
use tokio::runtime::Runtime;

#[no_mangle]
pub extern "system" fn Java_org_apache_arrow_datafusion_DefaultSessionContext_registerCsv(
    env: JNIEnv,
    _class: JClass,
    runtime: jlong,
    pointer: jlong,
    name: JString,
    path: JString,
    callback: JObject,
) {
    let runtime = unsafe { &mut *(runtime as *mut Runtime) };
    let name: String = env
        .get_string(name)
        .expect("Couldn't get name as string!")
        .into();
    let path: String = env
        .get_string(path)
        .expect("Couldn't get name as string!")
        .into();
    let context = unsafe { &mut *(pointer as *mut SessionContext) };
    runtime.block_on(async {
        let register_result = context
            .register_csv(&name, &path, CsvReadOptions::new())
            .await;
        let err_message: JValue = match register_result {
            Ok(_) => JValue::Void,
            Err(err) => {
                let err_message = env
                    .new_string(err.to_string())
                    .expect("Couldn't create java string!");
                err_message.into()
            }
        };
        env.call_method(callback, "accept", "(Ljava/lang/Object;)V", &[err_message])
            .expect("failed to callback method");
    });
}

#[no_mangle]
pub extern "system" fn Java_org_apache_arrow_datafusion_DefaultSessionContext_registerParquet(
    env: JNIEnv,
    _class: JClass,
    runtime: jlong,
    pointer: jlong,
    name: JString,
    path: JString,
    callback: JObject,
) {
    let runtime = unsafe { &mut *(runtime as *mut Runtime) };
    let name: String = env
        .get_string(name)
        .expect("Couldn't get name as string!")
        .into();
    let path: String = env
        .get_string(path)
        .expect("Couldn't get name as string!")
        .into();
    let context = unsafe { &mut *(pointer as *mut SessionContext) };
    runtime.block_on(async {
        let register_result = context
            .register_parquet(&name, &path, ParquetReadOptions::default())
            .await;
        let err_message: JValue = match register_result {
            Ok(_) => JValue::Void,
            Err(err) => {
                let err_message = env
                    .new_string(err.to_string())
                    .expect("Couldn't create java string!");
                err_message.into()
            }
        };
        env.call_method(callback, "accept", "(Ljava/lang/Object;)V", &[err_message])
            .expect("failed to callback method");
    });
}

#[no_mangle]
pub extern "system" fn Java_org_apache_arrow_datafusion_DefaultSessionContext_registerTable(
    env: JNIEnv,
    _class: JClass,
    runtime: jlong,
    pointer: jlong,
    name: JString,
    data: jbyteArray,
    print: JObject,
    callback: JObject,
) {
    let runtime = unsafe { &mut *(runtime as *mut Runtime) };
    let name: String = env
        .get_string(name)
        .expect("Couldn't get name as string!")
        .into();
    let byte_buf: Vec<u8> = env
        .convert_byte_array(data)
        .expect("Couldn't convert byte array")
        .into();
    let buff = std::io::Cursor::new(byte_buf);
    let reader = FileReader::try_new(BufReader::new(buff), None)
        .unwrap();

    let context = unsafe { &mut *(pointer as *mut SessionContext) };
    runtime.block_on(async {
        let schema = reader.schema();
        let mut batches = vec![];
        reader.for_each(|batch| {
            let mut bth = batch.unwrap();
            batches.push(bth);
        });

        let size = env.new_string(batches.get(0).unwrap().num_rows().to_string()).expect("").into();
        let mem_table = MemTable::try_new(schema, vec![batches]).unwrap();

        let register_result = context
            .register_table(name.as_str(), Arc::new(mem_table));

        env.call_method(print, "accept", "(Ljava/lang/Object;)V", &[size])
            .expect("failed to callback method");

        let err_message: JValue = match register_result {
            Ok(_) => JValue::Void,
            Err(err) => {
                let err_message = env
                    .new_string(err.to_string())
                    .expect("Couldn't create java string!");
                err_message.into()
            }
        };
        env.call_method(callback, "accept", "(Ljava/lang/Object;)V", &[err_message])
            .expect("failed to callback method");
    });
}

#[no_mangle]
pub extern "system" fn Java_org_apache_arrow_datafusion_DefaultSessionContext_querySql(
    env: JNIEnv,
    _class: JClass,
    runtime: jlong,
    pointer: jlong,
    sql: JString,
    callback: JObject,
) {
    let runtime = unsafe { &mut *(runtime as *mut Runtime) };
    let sql: String = env
        .get_string(sql)
        .expect("Couldn't get sql as string!")
        .into();
    let context = unsafe { &mut *(pointer as *mut SessionContext) };
    runtime.block_on(async {
        let query_result = context.sql(&sql).await;
        match query_result {
            Ok(v) => {
                let dataframe = Box::into_raw(Box::new(v)) as jlong;
                env.call_method(
                    callback,
                    "callback",
                    "(Ljava/lang/String;J)V",
                    &[JValue::Void, dataframe.into()],
                )
            }
            Err(err) => {
                let err_message = env
                    .new_string(err.to_string())
                    .expect("Couldn't create java string!");
                let dataframe = -1 as jlong;
                env.call_method(
                    callback,
                    "callback",
                    "(Ljava/lang/String;J)V",
                    &[err_message.into(), dataframe.into()],
                )
            }
        }
        .expect("failed to call method");
    });
}
#[no_mangle]
pub extern "system" fn Java_org_apache_arrow_datafusion_SessionContexts_destroySessionContext(
    _env: JNIEnv,
    _class: JClass,
    pointer: jlong,
) {
    let _ = unsafe { Box::from_raw(pointer as *mut SessionContext) };
}

#[no_mangle]
pub extern "system" fn Java_org_apache_arrow_datafusion_SessionContexts_createSessionContext(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    // let context = SessionContext::new();
    let mut config = context::SessionConfig::new();
    let context = SessionContext::with_config(config.clone());
    Box::into_raw(Box::new(context)) as jlong
}
