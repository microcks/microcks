/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.minion.async.format;

import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonDecoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Helper class using utility methods for converting Avro format from and to JSON.
 * @author laurent
 */
public class AvroUtil {

   /**
    * Convert a JSON string into an Avro binary representation using specified schema.
    * @param json A JSON string to convert to Avro
    * @param avroSchema String representation of an Avro Schema to use for conversion
    * @return The Avro binary representation of JSON
    * @throws AvroTypeException if there's a mismatch between JSON string and Avro Schema
    * @throws IOException if something goes wrong during conversion
    */
   public static byte[] jsonToAvro(String json, String avroSchema) throws AvroTypeException, IOException {
      return jsonToAvro(json, new Schema.Parser().parse(avroSchema));
   }

   /**
    * Convert a JSON string into an Avro binary representation using specified schema.
    * @param json A JSON string to convert to Avro
    * @param avroSchema The Avro Schema to use for conversion
    * @return The Avro binary representation of JSON
    * @throws AvroTypeException if there's a mismatch between JSON string and Avro Schema
    * @throws IOException if something goes wrong during conversion
    */
   public static byte[] jsonToAvro(String json, Schema avroSchema) throws AvroTypeException, IOException {
      // Prepare reader an input stream from Json string.
      GenericDatumReader<Object> reader = new GenericDatumReader<>(avroSchema);
      InputStream input = new ByteArrayInputStream(json.getBytes("UTF-8"));
      JsonDecoder jsonDecoder = DecoderFactory.get().jsonDecoder(avroSchema, input);

      // Prepare write and output stream to produce binary encoding.
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      GenericDatumWriter<Object> writer = new GenericDatumWriter<>(avroSchema);
      Encoder e = EncoderFactory.get().binaryEncoder(baos, null);

      // Fill a datum object from jsonDecoder.
      Object datum = null;
      try {
         while (true) {
            datum = reader.read(datum, jsonDecoder);
            writer.write(datum, e);
            e.flush();
         }
      } catch (EOFException eofException) {
         // Nothing to do here, we just exited the loop.
      }
      return baos.toByteArray();
   }

   /**
    * Convert an Avro binary representation into a JSON string using specified schema.
    * @param avroBinary An Avro binary representation to convert in JSON string
    * @param avroSchema The Avro Schema to use for conversion
    * @return The JSON string representing Avro binary
    * @throws AvroTypeException if there's a mismatch between Avro binary and Schema
    * @throws IOException if something goes wrong during conversion
    */
   public static String avroToJson(byte[] avroBinary, String avroSchema) throws AvroTypeException, IOException {
      return avroToJson(avroBinary, new Schema.Parser().parse(avroSchema));
   }

   /**
    * Convert an Avro binary representation into a JSON string using specified schema.
    * @param avroBinary An Avro binary representation to convert in JSON string
    * @param avroSchema The Avro Schema to use for conversion
    * @return The JSON string representing Avro binary
    * @throws AvroTypeException if there's a mismatch between Avro binary and Schema
    * @throws IOException if something goes wrong during conversion
    */
   public static String avroToJson(byte[] avroBinary, Schema avroSchema) throws AvroTypeException, IOException {
      DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(avroSchema);
      Decoder decoder = DecoderFactory.get().binaryDecoder(avroBinary, null);

      GenericRecord record = datumReader.read(null, decoder);
      return record.toString();
   }
}
