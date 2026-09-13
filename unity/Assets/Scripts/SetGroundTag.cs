using UnityEngine;

public class SetGroundTag : MonoBehaviour
{
    void Awake()
    {
        gameObject.tag = "Ground";
    }
}
