package Service;

import Dto.Result;
import PoJo.Patient;

public interface PatientLoginService {
    Result login(Patient patient);

    Result getCode( String username);

    void register(Patient patient);
}
