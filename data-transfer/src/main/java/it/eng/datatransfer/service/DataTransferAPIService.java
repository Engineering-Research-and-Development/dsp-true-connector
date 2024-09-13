package it.eng.datatransfer.service;

import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.Serializer;
import it.eng.datatransfer.repository.TransferProcessRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DataTransferAPIService {

	private TransferProcessRepository repository;
	
	public DataTransferAPIService(TransferProcessRepository repository) {
		super();
		this.repository = repository;
	}

	public Collection<JsonNode> findDataTransfers(String transferProcessId, String state) {
		if(StringUtils.isNotBlank(transferProcessId)) {
			return repository.findById(transferProcessId)
					.stream()
					.map(dt -> Serializer.serializePlainJsonNode(dt))
					.collect(Collectors.toList());
		} else if(StringUtils.isNoneBlank(state)) {
			return repository.findByState(state)
					.stream()
					.map(dt -> Serializer.serializePlainJsonNode(dt))
					.collect(Collectors.toList());
		}
		return repository.findAll().stream().map(dt -> Serializer.serializePlainJsonNode(dt))
				.collect(Collectors.toList());
		}
}
