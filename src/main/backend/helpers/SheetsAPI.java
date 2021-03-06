package helpers;
// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// [START sheets_quickstart]

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SheetsAPI
{
	private static final String APPLICATION_NAME = "LogParserTool - Jurisnoctis";
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static final String TOKENS_DIRECTORY_PATH = "tokens";
	private static StringHelper stringHelper = StringHelper.getInstance();
	private NetHttpTransport HTTP_TRANSPORT;
	private Sheets service;


	/**
	 * Global instance of the scopes required by this quickstart.
	 * If modifying these scopes, delete your previously saved tokens/ folder.
	 */
	private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
	private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

	/**
	 * Creates an authorized Credential object.
	 * @param HTTP_TRANSPORT The network HTTP Transport.
	 * @return An authorized Credential object.
	 * @throws IOException If the credentials.json file cannot be found.
	 */
	private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
		// Load client secrets.
		InputStream in = SheetsAPI.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
		if (in == null) {
			throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
		}
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
				.setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
				.setAccessType("offline")
				.build();
		LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
		return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
	}

	private Sheets getService() throws GeneralSecurityException, IOException
	{
		if(HTTP_TRANSPORT == null || service == null)
		{
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
					.setApplicationName(APPLICATION_NAME)
					.build();
		}
		return service;
	}

	private List<List<Object>> transform2dStringListInto2dObjectList(List<List<String>> input)
	{
		List<List<Object>> values = new ArrayList<>(input.size());
		for(List<String> rows : input)
		{
			List<Object> objectRow = new ArrayList<>();
			objectRow.addAll(rows);
			values.add(objectRow);
		}

		return values;
	}

	public List<List<String>> getRows(String spreadsheetId, String sheetName, String dataRange) throws GeneralSecurityException, IOException
	{
		dataRange = sheetName + "!" + dataRange;

		Sheets service = getService();

		Sheets.Spreadsheets.Values.Get request =
				service.spreadsheets().values().get(spreadsheetId, dataRange);
		request.setMajorDimension("ROWS");
		request.setRange(dataRange);

		ValueRange response = request.execute();

		return (List<List<String>>) response.get("values");
	}

	public void write2dDataStartingAt(String spreadsheetId, List<List<String>> data, String startingCell, String sheetName) throws IOException, GeneralSecurityException {
		//Build a new authorized API client service.
		String range = stringHelper.getRangeCalculation(data, startingCell);
		range = sheetName + "!" + range;
		Sheets service = getService();

		//Transform the data input into Objects
		List<List<Object>> values = transform2dStringListInto2dObjectList(data);

		ValueRange body = new ValueRange()
				.setValues(values);
		UpdateValuesResponse result =
				service.spreadsheets().values().update(spreadsheetId, range, body)
						.setValueInputOption("USER_ENTERED")
						.execute();

		System.out.printf("%d cells updated.", result.getUpdatedCells());
	}

	public void append2dData(String spreadsheetId, List<List<String>> data, String sheetName) throws IOException, GeneralSecurityException {
		//Build a new authorized API client service.
		String range = sheetName + "!" + "A1";
		Sheets service = getService();

		//Transform the data input into Objects
		List<List<Object>> values = transform2dStringListInto2dObjectList(data);
		AppendCellsRequest appendCellsRequest = new AppendCellsRequest();

		ValueRange body = new ValueRange()
				.setValues(values);
		AppendValuesResponse result =
				service.spreadsheets().values().append(spreadsheetId, range, body)
						.setValueInputOption("USER_ENTERED")
						.execute();
		System.out.println(result.getUpdates().getUpdatedRows() + " rows updated in the " + sheetName + " sheet.");
	}

	public void createSheet(String spreadsheetId, String newSheetName) throws IOException, GeneralSecurityException {
		//Build a new authorized API client service.
		Sheets service = getService();

		Request request = new Request();
		AddSheetRequest addSheetRequest = new AddSheetRequest();
		SheetProperties addSheetProperties = new SheetProperties();
		addSheetProperties.set("title", newSheetName);
		addSheetRequest.setProperties(addSheetProperties);
		request.setAddSheet(addSheetRequest);

		BatchUpdateSpreadsheetRequest batchUpdate = new BatchUpdateSpreadsheetRequest();
		List<Request> requests = new ArrayList<>();
		requests.add(request);
		batchUpdate.setRequests(requests);

		BatchUpdateSpreadsheetResponse result =
				service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate)
					.execute();
	}

	/**
	 * Prints the names and majors of students in a sample spreadsheet:
	 * https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
	 */
//	public static void main(String... args) throws IOException, GeneralSecurityException {
//		// Build a new authorized API client service.
//		final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
//		final String spreadsheetId = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms";
//		final String range = "Class Data!A2:E";
//		Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
//				.setApplicationName(APPLICATION_NAME)
//				.build();
//		ValueRange response = service.spreadsheets().values()
//				.get(spreadsheetId, range)
//				.execute();
//		List<List<Object>> values = response.getValues();
//		if (values == null || values.isEmpty()) {
//			System.out.println("No data found.");
//		} else {
//			System.out.println("Name, Major");
//			for (List row : values) {
//				// Print columns A and E, which correspond to indices 0 and 4.
//				System.out.printf("%s, %s\n", row.get(0), row.get(4));
//			}
//		}
//	}
}
// [END sheets_quickstart]